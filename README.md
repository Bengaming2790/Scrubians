# **Scrubians**

**Scrubians** is a **Fabric server-side NPC mod** for Minecraft that adds configurable, persistent NPCs with multiple behavior types.  
The mod is designed to be lightweight, extensible, and fully **JSON-driven**.

> **Server-side only** — no client mod required. (This mod will not work on the client-side whatsoever only use on a **Dedicated Server**)

---

## **Features Overview**

Scrubians introduces **three planned NPC types**:

| NPC Type | Status | Description |
|--------|--------|-------------|
| **Standard NPCs** | ✅ Completed | Passive, interactive NPCs using mannequins |
| **Violent NPCs** | 🚧 In Progress | Hostile NPCs that attack players in a defined area |
| **Boss NPCs** | ❌ Not Started | Powerful single NPCs with boss mechanics |

---
## **Commands**
| Command                                                        | Description                                                                                                                                                                                                                                   |
|----------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **/npc help**                                                  | Displays text in-game to understand the commands                                                                                                                                                                                              |
| **/npc create** {name} {skin} {attackable}                     | Creates an NPC *Both skin and Attackable are optional*                                                                                                                                                                                        |
| **/npc edit** {id} skin                                        | Edits the skin of the NPC on respawn                                                                                                                                                                                                          |
| **/npc edit** {id} path start                                  | displays all of the subcommands for creating a path for the current NPC. <br> *Those will not be documented here*                                                                                                                             |
| **/npc edit** {id} dialogue <br>{addpage/addoption/clear/view} | Used to create dialogue connected to an NPC. addpage followed by text will be what the NPC says. addoption will give choices along with actionIDs *(will be shown later)*. Clear clears all of the dialogue, and view shows you the dialogue. |
| **/npc removeall**                                             | Will remove all NPCs till next respawn                                                                                                                                                                                                        |
| **/npc respawn** {all/id}                                      | Will respawn all NPCs or a specific NPC by passing an ID                                                                                                                                                                                      |
| **/npc list**                                                  | Will provide the ID, name, and a way for the player to teleport to any NPC                                                                                                                                                                    |
| **/npc diagnose**                                              | Will find all NPCs that have issues related to their JSON                                                                                                                                                                                     | 
| **/npc debug**                                                 | Will provide the file location for the Scrubians JSON files <br> **ONLY GIVE TO PEOPLE WITH PANEL ACCESS**                                                                                                                                    |
 
---

## **Standard NPCs (Completed)**

Standard NPCs are **non-hostile**, interactive NPCs built using **Minecraft mannequins**.

### **Current Features**
- Uses **mannequins only**
- Fully persistent
- Supports:
  - Custom NPC names
  - Custom skins
  - Configurable walking paths
  - Dialogue options
---

## **Violent NPCs (In Progress)**

Violent NPCs are **hostile entities** intended for combat encounters.

### **Planned / In-Progress Features**
- Spawn within a defined **bounding box**
- Can spawn on **any solid block**
- Attack **any player** inside the bounding box
- Fully configurable:
  - Health
  - Attack damage
  - Quantity
- Can use **any entity type except mannequins**
  - Mannequin support planned for later

---

## **Boss NPCs (Planned)**

Boss NPCs will combine mechanics from **Standard** and **Violent** NPCs.

### **Planned Features**
- Only **one boss instance** spawned at a time
- Hostile combat behavior
- Boss health bar
- Custom stats and behaviors
- Persistent boss data

---

## **NPC Data Storage**

All NPCs in Scrubians are **data-driven**.

- NPCs are stored in **JSON files**
- Files are separated by NPC type:
  - Standard NPCs
  - Violent NPCs
  - Boss NPCs
- Enables:
  - Easy manual editing
  - Reloadable content
  - Future datapack-style workflows

---

## **Compatibility**

- **Mod Loader:** Fabric
- **Side:** Server-side only
- **Minecraft Version:** *1.21.10*
- **LuckPerms**
---

## **Roadmap**

- [x] Standard NPC system
- [x] Mannequin-based NPCs
- [ ] Violent NPC AI & combat balancing
- [ ] Mannequin support for Violent NPCs
- [ ] Boss NPC framework
- [ ] Boss health bars
- [ ] JSON format documentation
---

## **Contributing**

Contributions are welcome!

- Bug reports
- Feature requests
- Pull requests
- Documentation improvements

**If you are working on features not currently in the public release, you must set DEVELOPER_MODE to true in the Scrubians Class**

Open an issue or submit a PR to get started.
