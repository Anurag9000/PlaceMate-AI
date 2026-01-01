# PlaceMate AI 🏠🤖

PlaceMate is a multimodal, AI-powered inventory and location manager. It allows you to organize your home naturally using Vision, Voice, and nested Location hierarchies.

## 📖 How to Use

### 1. Adding & Removing Items
- **Add with Photo:** Tap the **(+) FAB** to open the Add Item screen. Click the **Camera Icon** to photograph an object. The AI will identify the item and suggest a category.
- **Add with Voice:** On the Add Item screen, tap the **Microphone Icon** and say something like *"Add my blue winter coat to the Bedroom Closet"*.
- **EDITABILITY:** After the AI processes a photo or voice command, **all text fields remain visible and fully editable**. You can manually refine the name, location, or notes before saving.
- **Remove/Return:** Find the item in the **Inventory** or **Taken Items** tab, click it to see details, and tap **"Mark Returned"** to update its status.

### 2. Multi-Modal Searching (The "Scan" Flow)
You can find where things are kept using our intelligent search:

#### **📸 Visual Search (Scan a Place)**
- On the **Inventory** or **Locations** tab, tap the **Camera Icon** in the search bar.
- Photograph a location (e.g., a "Shelf" or "Drawer").
- **AI Logic:** The app identifies the location and filters the list to show **exactly what objects are stored there in detail**.

#### **🎙️ Voice Search**
- Tap the **Microphone Icon** in the search bar.
- Say the name of an item or a location (e.g., *"Where is my hammer?"* or *"Show me the Kitchen Cabinet"*).
- The list will filter instantly to matching items.

---

## 💾 Database Architecture

PlaceMate is powered by **Android Room (SQLite)**, ensuring your data is stored locally, privately, and reliably.

### System Components:
- **Relational Mapping:** 
  - `ItemEntity`: Core item data (id, name, status, photo path).
  - `LocationEntity`: Hierarchical locations (Home > Room > Container).
  - `ItemPlacementEntity`: A mapping table that links specific items to their physical locations.
  - `BorrowEventEntity`: A historical log of every time an item was taken or returned.
- **Enums & Converters:** We use a custom `Converters` class to store complex types like `ItemStatus` (PRESENT, TAKEN) and `LocationType` (ROOM, STORAGE) as queryable strings.
- **Reactive Flow:** Every database operation is backed by **Kotlin Coroutines and Flow**, meaning the UI updates automatically as soon as the database state changes.

---

## 🛠️ Tech Stack
- **Language:** Kotlin
- **UI:** XML Layouts with ViewBinding
- **DI:** Hilt
- **AI/ML:** Google ML Kit (Computer Vision)
- **Database:** Room Persistence Library
- **Background Tasks:** WorkManager (for reminders)

---

## 🧱 Installation & Setup
1. Clone: `git clone https://github.com/Anurag9000/PlaceMate-AI.git`
2. Open in **Android Studio** (Ladybug+).
3. Build & Run: `./gradlew assembleDebug` (Min SDK 24).
