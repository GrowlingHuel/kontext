import os
import json
from google import genai

client = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])
BIBLE_PATH = "scripts/narrative_bible.json"

def finish_the_bible_with_total_memory():
    with open(BIBLE_PATH, "r") as f:
        bible = json.load(f)
    
    while len(bible) < 500:
        start_ch = bible[-1]["chapter"] + 1
        # We generate in chunks of 50 to ensure we don't hit the OUTPUT limit
        end_ch = min(start_ch + 49, 500)
        
        # OPTIMAL CONTEXT: We send the ENTIRE Bible generated so far
        # This ensures the 500th chapter is perfectly consistent with the 1st
        full_history = json.dumps(bible)
        
        prompt = f"""
        You are the Master Architect of the 'Worst Day in Leipzig' Surrealist Saga.
        
        FULL NARRATIVE HISTORY SO FAR:
        {full_history}
        
        TASK:
        Generate the next 50 chapters (Chapters {start_ch} to {end_ch}).
        
        REQUIREMENTS:
        1. INTERNAL CONSISTENCY: Refer back to previous locations, items, or 
           themes mentioned in the history. If a character appeared in Chapter 5, 
           they can reappear now.
        2. SURREAL ASCENT: The city should be significantly more 'broken' and 
           Kafkaesque than it was in the earlier chapters.
        3. CLIFFHANGERS: Ensure each chapter ends with a surreal hook.
        
        Output format: JSON list of objects only.
        """
        
        print(f"--- Generating Chapters {start_ch} to {end_ch} with Full History... ---")
        try:
            response = client.models.generate_content(
                model='gemini-2.5-flash',
                contents=prompt,
                config={'response_mime_type': 'application/json'}
            )
            
            new_chapters = json.loads(response.text)
            bible.extend(new_chapters)
            
            with open(BIBLE_PATH, "w") as f:
                json.dump(bible, f, indent=2)
            print(f"Current Progress: {len(bible)}/500 chapters.")
            
        except Exception as e:
            print(f"Error at {start_ch}: {e}. Retrying with full history...")
            continue

    print("🏁 THE BIBLE IS COMPLETE. 500 chapters of internally consistent Leipzig Purgatory.")

if __name__ == "__main__":
    finish_the_bible_with_total_memory()
