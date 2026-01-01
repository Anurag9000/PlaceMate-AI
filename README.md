# PlaceMate AI 🏠🤖

PlaceMate is a multimodal, AI-powered inventory and location manager for your home. It uses Computer Vision and Voice recognition to help you keep track of where things are and who has borrowed them.

## 🚀 Core Features

### 1. Multimodal Item Entry
You can add items to your inventory using three different methods:

#### **📸 AI Vision (Photo)**
- **How it works:** In the "Exclude Item" screen, tap the **Camera Icon**.
- **AI Logic:** The app uses Google's **ML Kit** to analyze the photo. It automatically identifies the object (e.g., identifies a "Hammer") and suggests a category (e.g., "Tools").
- **Manual Control:** Once identified, the **text becomes visible and fully editable**. You can tweak the name, description, or category before saving.

#### **🎙️ Voice Assistant**
- **How it works:** Tap the **Microphone Icon** on the Add Item screen or the Search bar.
- **AI Logic:** PlaceMate uses Android's Speech-to-Text engine. Our `InputInterpreter` parses your command (e.g., *"Put my blue umbrella in the hallway closet"*).
- **Editable Interaction:** The recognized text is piped directly into the input fields in real-time. You can review the transcribed text and manually edit it if the AI mishears a specific detail.

### 2. Smart Location Tracking
- Organize your home into nested locations (e.g., **Kitchen** > **Cabinet A** > **Top Shelf**).
- Items are linked to these locations, creating a searchable map of your home.

### 3. Borrow & Return system
- Mark items as **Taken** if a friend borrows them.
- Set a **Due Date** and the app will schedule a background reminder using **WorkManager**.

---

## 💾 Database System

PlaceMate uses the **Android Room Persistence Library**, which is an abstraction layer over **SQLite**.

### Key Technical Details:
- **Relational Tables:** 
  - `Items`: Stores object metadata and status.
  - `Locations`: Stores the physical hierarchy of your home.
  - `BorrowEvents`: Tracks the history of who took what and when.
  - `Reminders`: Manages scheduled notifications.
- **Type Converters:** Custom converters handle complex Kotlin Enums (`ItemStatus`, `LocationType`) as persistent strings.
- **Data Integrity:** Foreign Key constraints with `CASCADE` deletes ensure that if a location is deleted, all sub-locations and item mappings stay consistent.
- **Reactive Streams:** The UI uses **Kotlin Flows**, ensuring that as soon as the database updates, the UI reflects the change (e.g., an item appearing in "Taken Items" immediately after being marked).

---

## 🛠️ Tech Stack
- **Language:** Kotlin
- **Architecture:** MVVM (Model-View-ViewModel)
- **DI:** Hilt (Dependency Injection)
- **AI/ML:** ML Kit (Image Labeling)
- **Async:** Kotlin Coroutines & Flow
- **Background Tasks:** WorkManager

---

## 📥 Installation
1. Clone the repository.
2. Open in Android Studio (Ladybug or newer).
3. Build and run on an Android device (API 24+).
