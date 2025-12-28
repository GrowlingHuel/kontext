import csv
import os

# Input and Output paths
INPUT_FILE = "anki_export.txt"
OUTPUT_FILE = "app/src/main/res/raw/german_4000.csv"

def convert_anki_to_csv():
    print(f"Reading from {INPUT_FILE}...")
    
    if not os.path.exists(INPUT_FILE):
        print(f"Error: {INPUT_FILE} not found.")
        return

    cards = []

    # Read the Anki export file (assuming tab-separated or pipe-separated)
    # Detect delimiter? Let's assume Tab based on common Anki exports, or Pipe.
    # The user didn't specify strict format, so I'll try to sniff or fallback.
    
    delimiter = '\t' # Default for Anki
    
    try:
        with open(INPUT_FILE, 'r', encoding='utf-8') as f:
            # Check first line for delimiter hint if needed, or just standard csv reader
            line = f.readline()
            if '|' in line and '\t' not in line:
                delimiter = '|'
            f.seek(0)
            
            reader = csv.reader(f, delimiter=delimiter)
            
            for row in reader:
                # We expect at least 2 columns: German, English.
                # Ideally 4: German, English, ExampleDE, ExampleEN
                
                if len(row) < 2:
                    continue
                
                german = row[0].strip()
                english = row[1].strip()
                
                # Simple extraction of Example sentences if available
                # Often Anki cards have them in later fields or embedded HTML.
                # For this script, we'll take columns 2 and 3 if they exist, or empty strings.
                example_de = row[2].strip() if len(row) > 2 else ""
                example_en = row[3].strip() if len(row) > 3 else ""

                # Basic cleanup (remove HTML tags if any - rudimentary)
                german = german.replace("<b>", "").replace("</b>", "")
                english = english.replace("<b>", "").replace("</b>", "")

                cards.append([german, english, example_de, example_en])
    
    except Exception as e:
        print(f"Error reading file: {e}")
        return

    # Filter/Limit
    print(f"Total rows found: {len(cards)}")
    limit = 1000
    cards = cards[:limit]
    print(f"Keeping top {len(cards)} cards.")

    # Write to CSV
    try:
        with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            # Write Header
            writer.writerow(["German", "English", "ExampleDE", "ExampleEN"])
            writer.writerows(cards)
            
        print(f"Successfully wrote to {OUTPUT_FILE}")

    except Exception as e:
        print(f"Error writing file: {e}")

if __name__ == "__main__":
    convert_anki_to_csv()
