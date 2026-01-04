# **Scrubians**

**Scrubians** is a **Fabric server-side NPC mod** for Minecraft that adds configurable, persistent NPCs with multiple behavior types.  
The mod is designed to be lightweight, extensible, and fully **JSON-driven**.

> **Server-side only** — no client mod required.

---

## **Features Overview**

Scrubians introduces **three planned NPC types**:

| NPC Type | Status | Description |
|--------|--------|-------------|
| **Standard NPCs** | ✅ Completed | Passive, interactive NPCs using mannequins |
| **Violent NPCs** | 🚧 In Progress | Hostile NPCs that attack players in a defined area |
| **Boss NPCs** | ❌ Not Started | Powerful single NPCs with boss mechanics |

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

---

## **Roadmap**

- [x] Standard NPC system
- [x] Mannequin-based NPCs
- [ ] Violent NPC AI & combat balancing
- [ ] Mannequin support for Violent NPCs
- [ ] Boss NPC framework
- [ ] Boss health bars
- [ ] JSON format documentation
- [ ] Commands & admin tooling

---

## **Contributing**

Contributions are welcome!

- Bug reports
- Feature requests
- Pull requests
- Documentation improvements

Open an issue or submit a PR to get started.
