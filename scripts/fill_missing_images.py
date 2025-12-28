import os
import json
import time
from google import genai
from google.genai import types
from PIL import Image
import io

# --- CONFIGURATION ---
ASSETS_DIR = "app/src/main/assets"
MANIFEST_FILE = os.path.join(ASSETS_DIR, "stories.json")
TARGET_SIZE = (512, 512) # Resize to this (Width, Height)
IMAGE_QUALITY = 75       # JPG Quality (1-100)
# ---------------------

def compress_and_save(image_bytes, output_path):
    """Resizes and compresses image bytes before saving."""
    try:
        # 1. Open image from bytes
        img = Image.open(io.BytesIO(image_bytes))
        
        # 2. Resize (Antialias)
        img = img.resize(TARGET_SIZE, Image.Resampling.LANCZOS)
        
        # 3. Save with compression
        img.save(output_path, "JPEG", quality=IMAGE_QUALITY, optimize=True)
        print(f"  [✔] Saved Optimized Image: {output_path}")
        return True
    except Exception as e:
        print(f"  [!] Optimization Error: {e}")
        return False

def main():
    api_key = os.environ.get("GOOGLE_API_KEY")
    if not api_key:
        print("Error: GOOGLE_API_KEY environment variable not set.")
        return

    client = genai.Client(api_key=api_key)

    # 1. Load the Manifest
    if not os.path.exists(MANIFEST_FILE):
        print(f"Error: {MANIFEST_FILE} not found.")
        return

    try:
        with open(MANIFEST_FILE, "r") as f:
            stories = json.load(f)
    except json.JSONDecodeError:
        print("Error: stories.json is corrupt or empty.")
        return

    print(f"Loaded {len(stories)} stories from manifest.")

    # 2. Iterate and Backfill
    for story in stories:
        batch_id = story.get("batch_id")
        image_prompt = story.get("image_prompt")
        
        if not batch_id or not image_prompt:
            continue

        filename = f"story_{batch_id}.jpg"
        output_path = os.path.join(ASSETS_DIR, filename)

        # CHECK: Does the image already exist?
        if os.path.exists(output_path):
            print(f"Skipping Batch {batch_id}: Image already exists.")
            continue

        print(f"Generating Image for Batch {batch_id}...")
        
        try:
            # 3. Call Imagen 4
            response = client.models.generate_images(
                model='imagen-4.0-generate-001', # Or 'imagen-4.0-generate-001' if available
                prompt=image_prompt,
                config=types.GenerateImagesConfig(
                    number_of_images=1,
                    aspect_ratio="1:1" # Square aspect ratio
                )
            )

            if response.generated_images:
                # 4. Resize & Save
                raw_bytes = response.generated_images[0].image.image_bytes
                compress_and_save(raw_bytes, output_path)
            else:
                print(f"  [!] API returned no images for Batch {batch_id}")

            # Sleep to respect rate limits
            time.sleep(4) 

        except Exception as e:
            print(f"  [!] Failed to generate image for Batch {batch_id}: {e}")

    print("\n--- Backfill Complete ---")

if __name__ == "__main__":
    main()
