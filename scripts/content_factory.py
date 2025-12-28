
from google import genai
from google.genai import types
import csv
import json
import time
import os
import argparse
import traceback
from pathlib import Path

# Configuration & Path Safety
SCRIPT_DIR = Path(__file__).parent.absolute()
PROJECT_ROOT = SCRIPT_DIR.parent
ASSETS_DIR = PROJECT_ROOT / "app/src/main/assets"
CSV_PATH = PROJECT_ROOT / "app/src/main/res/raw/german_4000.csv"
OUTPUT_JSON = ASSETS_DIR / "stories.json"
BATCH_SIZE = 10

def setup_args():
    parser = argparse.ArgumentParser(description="Generate German stories and images using Gemini & Imagen (google-genai SDK).")
    parser.add_argument("--limit", type=int, default=1, help="Limit the number of batches to process (default: 1).")
    parser.add_argument("--start-batch", type=int, default=0, help="Force start from specific batch ID (optional).")
    return parser.parse_args()

def configure_client():
    api_key = os.environ.get("GOOGLE_API_KEY")
    if not api_key:
        raise ValueError("GOOGLE_API_KEY environment variable not set.")
    return genai.Client(api_key=api_key)

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

def generate_story_text(client, batch_id, words):
    word_list_str = ", ".join(words)
    print(f"Generating TEXT for Batch {batch_id}...")
    
    prompt = f"""
    You are an expert German teacher.
    Create a short A1-level story using these words: [{word_list_str}].

    1. Write a version for a Male main character named {{{{HERO}}}}. Ensure all pronouns (er/sein) and adjective endings are masculine.
    2. Write a version for a Female main character named {{{{HERO}}}}. Ensure all pronouns (sie/ihr) and adjective endings are feminine.
    3. Create a single Image Prompt that describes the scene/action but keeps the protagonist silhouette-based, first-person, or distant so it fits either gender.

    Return ONLY a valid, parseable JSON object with this exact structure:
    {{
      "story_M": {{ "de": "German text here...", "en": "English translation here..." }},
      "story_F": {{ "de": "German text here...", "en": "English translation here..." }},
      "image_prompt": "Image prompt here..."
    }}
    """

    try:
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
            config=types.GenerateContentConfig(response_mime_type='application/json')
        )
        
        # Parse JSON
        text = response.text
        # Normally cleaner with response_mime_type, but let's double check valid JSON parse
        return json.loads(text)
    except Exception as e:
        print(f"Error generating text for batch {batch_id}: {e}")
        traceback.print_exc()
        return None

def generate_image(client, prompt, output_path):
    print(f"Generating IMAGE for: {prompt[:50]}...")
    try:
        # NOTE: Imagen 4 logic as requested
        # 'imagen-4.0-generate-001' or user specified
        response = client.models.generate_images(
            model='imagen-4.0-generate-001',
            prompt=prompt,
            config=types.GenerateImagesConfig(number_of_images=1)
        )
        
        if response.generated_images:
            image_bytes = response.generated_images[0].image.image_bytes
            with open(output_path, "wb") as f:
                f.write(image_bytes)
            print(f"SUCCESS: Saved image to {output_path}")
        else:
            print("ERROR: No images returned in response.")
            
    except Exception as e:
        print(f"Error generating image: {e}")
        traceback.print_exc()

def main():
    args = setup_args()
    
    if not ASSETS_DIR.exists():
        ASSETS_DIR.mkdir(parents=True)

    client = configure_client()
    all_vocab = read_vocab(CSV_PATH)
    total_words = len(all_vocab)
    
    # Load existing data
    current_data = []
    if OUTPUT_JSON.exists():
        try:
            with open(OUTPUT_JSON, 'r') as f:
                current_data = json.load(f)
        except:
            current_data = []

    # Determine batch start
    next_batch_id = 1
    if current_data:
        next_batch_id = max([d.get('batch_id', 0) for d in current_data]) + 1
    
    if args.start_batch > 0:
        next_batch_id = args.start_batch

    print(f"Starting Process. Limit: {args.limit} batches.")
    
    processed_count = 0
    start_index = (next_batch_id - 1) * BATCH_SIZE
    
    for i in range(start_index, total_words, BATCH_SIZE):
        if processed_count >= args.limit:
            print(f"Reached limit of {args.limit} batches.")
            break
            
        batch_vocab = all_vocab[i:i+BATCH_SIZE]
        if not batch_vocab:
            break
            
        print(f"Processing Batch {next_batch_id} (Words: {batch_vocab})")
        
        # 1. Text Generation
        data = generate_story_text(client, next_batch_id, batch_vocab)
        
        if data:
            entry = {
                "batch_id": next_batch_id,
                "target_words": batch_vocab,
                "image_prompt": data.get("image_prompt"),
                "story_M": data.get("story_M"),
                "story_F": data.get("story_F")
            }
            
            # Save generic placeholder/data now
            current_data.append(entry)
            with open(OUTPUT_JSON, 'w') as f:
                json.dump(current_data, f, indent=2, ensure_ascii=False)
            
            # 2. Image Generation
            if entry.get("image_prompt"):
                img_filename = f"story_{next_batch_id}.jpg"
                img_path = ASSETS_DIR / img_filename
                generate_image(client, entry["image_prompt"], img_path)

            print(f"Batch {next_batch_id} completed.")
            next_batch_id += 1
            processed_count += 1
            
            if processed_count < args.limit:
                time.sleep(2) 
        else:
            print(f"Skipping batch {next_batch_id} due to text error.")
            # We don't increment processed_count here so we try to hit limit=1 success
            pass

if __name__ == "__main__":
    main()
