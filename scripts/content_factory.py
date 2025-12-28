
from google import genai
from google.genai import types
import csv
import json
import time
import os
import argparse
import traceback
import random
import io
import datetime
from pathlib import Path
from PIL import Image

# Configuration & Path Safety
SCRIPT_DIR = Path(__file__).parent.absolute()
PROJECT_ROOT = SCRIPT_DIR.parent
ASSETS_DIR = PROJECT_ROOT / "app/src/main/assets"
CSV_PATH = PROJECT_ROOT / "app/src/main/res/raw/german_4000.csv"
BIBLE_PATH = SCRIPT_DIR / "narrative_bible.json"
OUTPUT_JSON = ASSETS_DIR / "stories.json"
BATCH_SIZE = 10
LEGACY_POOL_SIZE = 5
ANCHOR_POOL_SIZE = 100

def setup_args():
    parser = argparse.ArgumentParser(description="Generate 'Magritte-Style' German CYOA stories.")
    parser.add_argument("--limit", type=int, default=1, help="Limit the number of batches to process (default: 1).")
    parser.add_argument("--start-batch", type=int, default=0, help="Force start from specific batch ID (optional).")
    return parser.parse_args()

def configure_client():
    api_key = os.environ.get("GOOGLE_API_KEY")
    if not api_key:
        raise ValueError("GOOGLE_API_KEY environment variable not set.")
    return genai.Client(api_key=api_key)

def read_json_file(filepath):
    if not filepath.exists():
        return []
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def read_vocab(filepath):
    words = []
    if not filepath.exists():
        print(f"Error: Vocab file not found at {filepath}")
        return []
        
    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f, delimiter='|')
        for row in reader:
            if 'german' in row and row['german']:
                words.append(row['german'])
    return words

def get_anchor_words(all_words):
    # Top 100 words (70% weight logic in prompt availability)
    return all_words[:ANCHOR_POOL_SIZE]

def get_legacy_words(all_words, current_index):
    # Pull 5 random words from previous batches (20% logic)
    if current_index < BATCH_SIZE:
        return []
    pool = all_words[:current_index]
    count = min(len(pool), LEGACY_POOL_SIZE)
    return random.sample(pool, count)

def create_story_cache(client, bible_data, anchor_words):
    print("Creating Context Cache (The Leipzig Purgatory)...")
    
    bible_str = json.dumps(bible_data, indent=2)
    anchors_str = ", ".join(anchor_words)
    
    system_instruction = f"""
    You are The Leipzig Surrealist, an expert German language author.
    
    **The World (Narrative Bible):**
    {bible_str}
    
    **Anchor Vocabulary (Top 100):**
    [{anchors_str}]
    
    **Role:**
    Generate Choose-Your-Own-Adventure (CYOA) content for German learners.
    
    **Image Style (Magritte):**
    - "Literal Surrealism": Clear daylight, sharp focus, normal everyday Leipzig life (cafes, trams, streets).
    - NO "Goth", "Dark", "Creepy", or "Mist". The strangeness comes from the situational absurdity, not the lighting.
    - Hero Reference: Always refer to the protagonist as "our hero" or "the traveler" in image prompts. NEVER specified gender for the hero.
    - NPCs: Explicitly specify gender (e.g., "an elderly female baker").

    **Text Structure (Sentence Arrays):**
    - Instead of block paragraphs, you must return content as LISTS of single sentences.
    - 'sentences_m', 'sentences_f', and 'en_sentences' MUST have the exact same number of items.
    - Index [0] of 'sentences_m' must correspond to Index [0] of 'en_sentences', etc.
    """
    
    # Attempt Primary -> Fallback
    models_to_try = ['gemini-2.5-flash', 'gemini-2.0-flash'] # Prefer 2.5
    
    for model_name in models_to_try:
        try:
            print(f"Attempting cache creation with {model_name}...")
            cache = client.caches.create(
                model=model_name,
                config=types.CreateCachedContentConfig(
                    display_name="leipzig_magritte_cache",
                    system_instruction=system_instruction,
                    ttl="3600s"
                )
            )
            print(f"Cache created: {cache.name} using {model_name}")
            return cache.name, model_name
        except Exception as e:
            print(f"Warning: Cache failed with {model_name} ({e}).")
            
    print("Masking cache failure. Will proceed with manual context injection using gemini-2.5-flash.")
    return None, 'gemini-2.5-flash'

def generate_cyoa_content(client, level, new_words, legacy_words, cache_name, model_name, bible_data):
    # Context
    idx = (level - 1) % len(bible_data)
    next_idx = level % len(bible_data)
    
    current_chapter = bible_data[idx]
    next_chapter = bible_data[next_idx]
    
    context = {
        "location": current_chapter.get("location", "Unknown"),
        "vibe": current_chapter.get("vibe", "Normal"),
        "destination": next_chapter.get("location", "Unknown")
    }

    words_combined_str = ", ".join(new_words + legacy_words)
    
    print(f"Generating LEVEL {level} [Magritte Style] @ {context['location']} -> {context['destination']} using {model_name}")

    prompt = f"""
    **Level:** {level}
    **Location:** {context['location']}
    **Vibe:** {context['vibe']} (Interpret as Magritte-style surrealism)
    **Bottleneck Destination:** {context['destination']}
    
    **Target Vocabulary (Must Use):** [{words_combined_str}]
    
    **Task:**
    1. Start at {context['location']}.
    2. Offer TWO choices (Path A and Path B). Both must inevitably lead towards {context['destination']}.
    3. **Tone:** Banal Surrealism. Ordinary objects behaving strangely in broad daylight.
    4. **Output Format:**
       - Generate 2-3 sentences per path choice.
       - Return strict JSON arrays for sentences.
       - Ensure Male/Female German versions differ only by grammatical necessity (pronouns/endings). Hero name is '{{{{HERO}}}}'.

    **JSON Structure:**
    {{
      "level": {level},
      "choice_a": {{
         "prompt": "Choice A Label",
         "sentences_m": ["Sentence 1 (M)...", "Sentence 2 (M)..."],
         "sentences_f": ["Sentence 1 (F)...", "Sentence 2 (F)..."],
         "en_sentences": ["Sentence 1 (EN)...", "Sentence 2 (EN)..."],
         "image_prompt": "Magritte style image prompt for A..."
      }},
      "choice_b": {{
         "prompt": "Choice B Label",
         "sentences_m": ["..."],
         "sentences_f": ["..."],
         "en_sentences": ["..."],
         "image_prompt": "Magritte style image prompt for B..."
      }}
    }}
    """

    generate_config = types.GenerateContentConfig(
        response_mime_type='application/json'
    )
    
    try:
        if cache_name:
             response = client.models.generate_content(
                model=model_name,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type='application/json',
                    cached_content=cache_name
                )
            )
        else:
             response = client.models.generate_content(
                model=model_name,
                contents=prompt,
                config=generate_config
            )

        return json.loads(response.text)
    except Exception as e:
        print(f"Error generating text for level {level}: {e}")
        traceback.print_exc()
        return None

def process_and_save_image(image_bytes, output_path):
    try:
        img = Image.open(io.BytesIO(image_bytes))
        img = img.resize((512, 512), Image.Resampling.LANCZOS)
        img.convert("RGB").save(output_path, "JPEG", quality=70) 
        print(f"  [+] Saved: {output_path} (512x512 Q70 Magritte)")
    except Exception as e:
        print(f"  [!] Optimizing failed: {e}")

def generate_image(client, prompt, output_path):
    print(f"  > Gen Image: {prompt[:50]}...")
    try:
        response = client.models.generate_images(
            model='imagen-4.0-generate-001', # Hardcoded 4.0
            prompt=prompt,
            config=types.GenerateImagesConfig(number_of_images=1)
        )
        if response.generated_images:
            process_and_save_image(response.generated_images[0].image.image_bytes, output_path)
        else:
            print("  [!] No images returned.")
    except Exception as e:
        print(f"  [!] Imagen Error: {e}")

def main():
    args = setup_args()
    if not ASSETS_DIR.exists(): ASSETS_DIR.mkdir(parents=True)

    client = configure_client()
    all_vocab = read_vocab(CSV_PATH)
    bible_data = read_json_file(BIBLE_PATH)
    
    if not bible_data:
        print("Error: No Narrative Bible found.")
        return

    anchor_words = get_anchor_words(all_vocab)
    cache_name, model_used = create_story_cache(client, bible_data, anchor_words)
    
    # Load existing
    current_data = []
    if OUTPUT_JSON.exists():
        try:
            with open(OUTPUT_JSON, 'r') as f: current_data = json.load(f)
        except: current_data = []

    next_level = 1
    if current_data:
        next_level = max([d.get('level', 0) for d in current_data]) + 1
    if args.start_batch > 0:
        next_level = args.start_batch

    print(f"Starting Magritte Factory. Batch Limit: {args.limit}")
    
    processed = 0
    start_index = (next_level - 1) * BATCH_SIZE
    total_words = len(all_vocab)

    for i in range(start_index, total_words, BATCH_SIZE):
        if processed >= args.limit: break
        
        # Words
        new_batch = all_vocab[i:i+BATCH_SIZE]
        if not new_batch: break
        legacy = get_legacy_words(all_vocab, i)
        
        # Text
        story = generate_cyoa_content(client, next_level, new_batch, legacy, cache_name, model_used, bible_data)
        
        if story:
            entry = {
                "level": next_level,
                "target_words": new_batch,
                "legacy_words": legacy,
                "choice_a": story.get("choice_a"),
                "choice_b": story.get("choice_b")
            }
            current_data.append(entry)
            
            # Save JSON
            with open(OUTPUT_JSON, 'w') as f:
                json.dump(current_data, f, indent=2, ensure_ascii=False)
                
            # Images
            if entry.get("choice_a"):
                generate_image(client, entry["choice_a"].get("image_prompt", "magritte scene"), ASSETS_DIR / f"story_{next_level}_A.jpg")
            if entry.get("choice_b"):
                generate_image(client, entry["choice_b"].get("image_prompt", "magritte scene"), ASSETS_DIR / f"story_{next_level}_B.jpg")
                
            print(f"Level {next_level} Complete.")
            next_level += 1
            processed += 1
            if processed < args.limit: time.sleep(1)
            
        else:
            print(f"Skipping L{next_level}")

if __name__ == "__main__":
    main()
