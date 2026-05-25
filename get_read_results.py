import json, sys, os

transcript_path = r'C:\Users\Me\.cursor\projects\c-Users-Me-Desktop-guard-native\agent-transcripts\9fdb8d8c-41cc-4d01-a0a7-d54b015e2467\9fdb8d8c-41cc-4d01-a0a7-d54b015e2467.jsonl'

with open(transcript_path, 'r', encoding='utf-8', errors='replace') as f:
    lines_raw = f.readlines()

# Check structure around events 104-110 (Read calls and their results)
for i in range(103, 115):
    ev = json.loads(lines_raw[i])
    content_str = json.dumps(ev)
    print(f'Event {i}: role={ev.get("role","?")} len={len(content_str)}')
    # Show the structure
    msg = ev.get('message', {})
    if isinstance(msg, dict):
        for item in msg.get('content', []):
            if isinstance(item, dict):
                t = item.get('type', '?')
                n = item.get('name', '')
                tool_use_id = item.get('tool_use_id', '')
                text = item.get('text', '')
                print(f'  item: type={t}, name={n}, tool_use_id={tool_use_id[:20] if tool_use_id else ""}, text_len={len(str(text))}')
                if 'ConvFilter' in str(text)[:100]:
                    print(f'  -> has ConvFilter in text start')
    print()
