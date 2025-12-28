import os
import json
from google import genai

# Configuration
MASTER_LOCATION = "Leipzig, Germany"
TOTAL_CHAPTERS = 500

def generate_narrative_bible():
    client = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])
    
    prompt = f"""
    Create a Master Narrative Bible for a 500-chapter surrealist story set in {MASTER_LOCATION}.
    The story follows {{HERO}} on their 'Worst Day Ever' which lasts for millennia.
    
    Structure:
    - Chapters 1-10: Mundane Leipzig (Cafes, Bakeries, Pubs).
    - Chapters 11-100: Slowly warping reality (Doors lead to wrong rooms, clocks run backward).
    - Chapters 101-500: Full surrealist purgatory (The city is made of glass, people are shadows, language is the only constant).
    
    Output Format: A JSON list of objects:
    [
      {{"chapter": 1, "location": "Cafe Baum", "vibe": "Rainy, familiar", "cliffhanger": "The waiter has no mouth."}},
      ...
    ]
    
    Generate 500 concise chapter beats. Keep the 'vibe' and 'cliffhanger' to one sentence each.
    """

    print("Generating 500-chapter Narrative Bible... (This may take a minute)")
    response = client.models.generate_content(
        model='gemini-2.0-flash',
        contents=prompt,
        config={'response_mime_type': 'application/json'}
    )
    
    with open("scripts/narrative_bible.json", "w") as f:
        f.write(response.text)
    print("SUCCESS: narrative_bible.json created.")

if __name__ == "__main__":
    generate_narrative_bible()
