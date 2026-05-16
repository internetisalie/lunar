import os
import re
import sys

def align_icons(directory):
    # Mapping of status to icon (Only Done/Completed)
    status_to_icon = {
        "done": "✅",
        "completed": "✅",
    }
    
    # regex to find frontmatter
    fm_regex = re.compile(r'^---\s*\n(.*?)\n---\s*\n', re.DOTALL | re.MULTILINE)
    
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(".md"):
                filepath = os.path.join(root, file)
                with open(filepath, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                match = fm_regex.search(content)
                if not match:
                    continue
                
                fm_text = match.group(1)
                lines = fm_text.splitlines()
                
                # Parse basic YAML-like structure
                fm_dict = {}
                for line in lines:
                    if ':' in line:
                        k, v = line.split(':', 1)
                        fm_dict[k.strip()] = v.strip()
                
                if 'status' not in fm_dict:
                    continue
                
                status = fm_dict['status'].lower()
                target_icon = status_to_icon.get(status)
                
                new_lines = []
                changed = False
                
                # First pass: check if we need to remove or change icons
                has_icon = 'vf_icon' in fm_dict
                existing_icon = fm_dict.get('vf_icon')
                
                if has_icon and existing_icon != target_icon:
                    changed = True
                elif not has_icon and target_icon:
                    changed = True
                
                if not changed:
                    continue

                # Rebuild lines
                for line in lines:
                    if line.strip().startswith('vf_icon:'):
                        continue # Skip existing icon line
                    
                    new_lines.append(line)
                    if line.strip().startswith('status:') and target_icon:
                        new_lines.append(f"vf_icon: {target_icon}")

                new_fm = "---\n" + "\n".join(new_lines) + "\n---"
                new_content = fm_regex.sub(new_fm + "\n", content, count=1)
                
                if new_content != content:
                    with open(filepath, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Updated: {filepath} (status: {status})")

if __name__ == "__main__":
    # Use environment relative path or default to features dir
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    features_dir = os.path.join(base_dir, "features")
    
    if not os.path.exists(features_dir):
        # Fallback for manual execution from skill dir
        features_dir = "/home/mini/Documents/src/lua/lunar/docs/features"

    align_icons(features_dir)
