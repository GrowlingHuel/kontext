import csv
import re
import os

# Configuration
INPUT_FILE = "anki_export.txt"
OUTPUT_FILE = "app/src/main/res/raw/german_4000.csv"

def clean_text(text):
    """Removes HTML tags and extra whitespace."""
    if not text:
        return ""
    # Remove HTML tags
    clean = re.sub('<[^<]+?>', '', text)
    # Remove wrapping quotes if any (though csv writer handles this, sometimes the source has explicit ones)
    clean = clean.strip().strip('"').strip("'")
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
                line = line.strip()
                
                # 1. Skip Metadata
                if line.startswith('#'):
                    skipped_metadata += 1
                    continue
                
                if not line:
                    continue

                # 2. Split by Tab
                parts = line.split('\t')

                # 3. Safety Check: Index 6 is the 7th column, so we need at least 7 columns
                # Indices requested: 1, 4, 5, 6
                if len(parts) < 7:
                    # Log error for debugging but don't crash
                    # print(f"Skipping line {line_num}: Not enough columns ({len(parts)} found)")
                    skipped_short += 1
                    continue

                try:
                    # 4. Extract Fields
                    word_de = clean_text(parts[1])
                    def_en = clean_text(parts[4])
                    sent_de = clean_text(parts[5])
                    sent_en = clean_text(parts[6])

                    # Basic validation: Must have a german word
                    if not word_de:
                        continue

                    cards.append([word_de, def_en, sent_de, sent_en])
                    success_count += 1

                except IndexError:
                    # Should be covered by len check, but just in case
                    skipped_short += 1
                    continue

    except Exception as e:
        print(f"Critical Error reading file: {e}")
        return

    # Limit to 1000 for now if needed, or keep all valid
    # User said "Keep only the top 1000" in previous prompt, but here "Write a robust..." 
    # and didn't explicitly repeat the limit, but implicitly for "german_4000.csv" it might handle more.
    # However, for the prototype, let's stick to the top 1000 to keep it manageable and fast.
    # Actually, the user's previous prompt said "german_4000.csv" but limit 1000. 
    # I'll check if I should limit. User didn't strictly say "limit" in THIS prompt, but consistency is good.
    # I'll keep all valid ones but maybe limit to 4000 if the filename implies it?
    # Let's write ALL valid ones found. If the file is 5000 lines, so be it. 
    # Wait, the user said "german_4000.csv" in the task name.
    # I will write all extracted cards.
    
    # 5. Output to CSV
    try:
        with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
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
