import csv
import re
import os

# Configuration
INPUT_FILE = "anki_export.txt"
OUTPUT_FILE = "app/src/main/res/raw/german_4000.csv"

def clean_text(text):
    """Removes HTML tags, extra whitespace, and quotes."""
    if not text:
        return ""
    # Remove HTML tags
    clean = re.sub('<[^<]+?>', '', text)
    # Remove extra whitespace
    clean = clean.strip()
    return clean

def main():
    print(f"Processing {INPUT_FILE}...")
    
    if not os.path.exists(INPUT_FILE):
        print(f"Error: {INPUT_FILE} not found.")
        return

    cards = []
    skipped_metadata = 0
    skipped_short = 0
    success_count = 0

    try:
        with open(INPUT_FILE, 'r', encoding='utf-8') as f:
            for line_num, line in enumerate(f, 1):
                clean_line = line.strip()
                
                # 1. Skip Headers / Metadata
                if clean_line.startswith('#'):
                    skipped_metadata += 1
                    continue
                
                if not clean_line:
                    continue

                # 2. Split by Tab
                parts = line.split('\t') # Do not strip line before split to preserve tab counts if empty at end? 
                                         # Actually Anki export usually has tabs. `line.strip()` above removed newline.
                                         # But `split('\t')` on a stripped line is fine.
                                         # Wait, if I strip, I might lose trailing empty fields if they are tab separated?
                                         # But `line.strip()` removes whitespace from ends.
                                         # Let's use `line.rstrip('\n')` to be safer about tabs.
                
                parts = line.rstrip('\n').split('\t')

                # 3. Column Mapping (0-based)
                # German: 1
                # English: 4
                # Sentence (DE): 5
                # Sentence (EN): 6
                
                # We need at least index 6 to exist (7 columns)
                if len(parts) < 7:
                    skipped_short += 1
                    continue

                try:
                    raw_german = clean_text(parts[1])
                    english = clean_text(parts[4])
                    sent_de = clean_text(parts[5])
                    sent_en = clean_text(parts[6])

                    if not raw_german:
                        continue

                    # 4. Cleaning German: Take part before first comma
                    german = raw_german.split(',')[0].strip()

                    cards.append([german, english, sent_de, sent_en])
                    success_count += 1

                except IndexError:
                    skipped_short += 1
                    continue

    except Exception as e:
        print(f"Critical Error reading file: {e}")
        return

    # 5. Output to CSV
    try:
        with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f, delimiter='|')
            # Headers: german,english,example_de,example_en
            writer.writerow(["german", "english", "example_de", "example_en"])
            writer.writerows(cards)
            
        print(f"Success! Processed {line_num} lines.")
        print(f"  - Skipped (Metadata): {skipped_metadata}")
        print(f"  - Skipped (Too short): {skipped_short}")
        print(f"  - Extracted: {success_count}")
        print(f"Wrote to {OUTPUT_FILE}")

    except Exception as e:
        print(f"Error writing CSV: {e}")

if __name__ == "__main__":
    main()
