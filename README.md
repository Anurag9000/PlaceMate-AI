# PlaceMate AI 🏠🤖

PlaceMate is a multimodal, AI-powered inventory and location manager. It allows you to organize your home naturally using Vision, Voice, and nested Location hierarchies.

## 📖 How to Use

### 1. Adding & Removing Items
- **Add with Photo:** Tap the **(+) FAB** to open the Add Item screen. Click the **Camera Icon** to photograph an object. The AI will identify the item and suggest a category.
- **Add with Voice:** On the Add Item screen, tap the **Microphone Icon** and say something like *"Add my blue winter coat to the Bedroom Closet"*.
- **EDITABILITY:** After the AI processes a photo or voice command, **all text fields remain visible and fully editable**. You can manually refine the name, location, or notes before saving.

### 2. Multi-Modal Searching (The "Scan" Flow)
You can find where things are kept using our intelligent search:

#### **📸 Visual Search (Scan a Place)**
- On the **Inventory** or **Locations** tab, tap the **Camera Icon** in the search bar.
- Photograph a location (e.g., a "Shelf" or "Drawer").
- **AI Logic:** The app identifies the location and filters the list to show **exactly what objects are stored there in detail**.

#### **🎙️ Voice Search**
- Tap the **Microphone Icon** in the search bar.
- Say the name of an item or a location (e.g., *"Where is my hammer?"* or *"Show me the Kitchen Cabinet"*).
- **🧠 Semantic Intelligence (Synonyms):** The app understands synonyms. If you say *"Show me the lounge"*, it automatically knows you mean the **"Living Room"** and filters accordingly.

---

## ✨ Semantic Intelligence & Synonyms
We have implemented a `SynonymManager` that bridges the gap between different ways humans talk:
- **Locations:** "Kitchen" ↔ "Kitchenette" ↔ "Pantry"
- **Furniture:** "Couch" ↔ "Sofa" ↔ "Settee"
- **General:** "Box" ↔ "Container" ↔ "Bin"

This ensures that regardless of whether the AI sees a "Pantry" or you say "Kitchenette," the app correctly resolves to your defined locations.

---

## 💾 Database Architecture
PlaceMate is powered by **Android Room (SQLite)**, ensuring your data is stored locally, privately, and reliably.
- **Relational Mapping:** `ItemEntity` and `LocationEntity` are linked via a placement bridge.
- **Join Queries:** Our search logic joins these tables, allowing the AI's visual results to instantly list the items within a scanned room.

---

## 🏗️ Tech Stack
- **Language:** Kotlin
- **AI/ML:** Google ML Kit (Computer Vision) & Speech-to-Text
- **Database:** Room Persistence Library
- **Architecture:** MVVM + Hilt (DI)

---

## 🧱 Installation
1. Clone: `git clone https://github.com/Anurag9000/PlaceMate-AI.git`
2. Build & Run: `./gradlew assembleDebug` (Min SDK 24).
