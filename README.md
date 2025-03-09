# 👗 SwapStyle - Clothing Swap App 

<p align="center">
  <img src="photos/app_logo.png" alt="SwapStyle Logo" width="150">
</p>

## 📌 Introduction
SwapStyle is a modern Android application that enables users to swap clothing items with others in their community. The app promotes sustainable fashion by encouraging reuse rather than disposal of clothing items, creating a circular economy for fashion enthusiasts.

This application leverages **Firebase Authentication, Firestore Database, and Firebase Storage** to manage users, items, and swap offers efficiently.

---

## 🚀 Features

### 🔑 User Authentication
• **Sign Up / Login** with Firebase Authentication.  
• **Password Reset** for recovering accounts.  
• **Profile Management** including profile picture updates.

### 👕 Clothing Management
• **Upload clothing items** with images, descriptions, and size details.  
• **Categorized browsing** for easy discovery (Men, Women, Kids, Accessories).  
• **Favorite items** to save them for later.  
• **Advanced search** for filtering by title, brand, category, or size.

### 🔄 Swap System
• **Send swap offers** for available items.  
• **Set preferred swap locations & time slots.**  
• **Real-time notifications** for accepted/rejected offers.  
• **Swap history tracking** for completed exchanges.

### 🎨 UI/UX Enhancements
• **Smooth animations & transitions** using `AnimationHelper`.  
• **Image picker & cropper** for optimized uploads.  
• **Google Maps API** for location-based swapping.

---

## 📲 User Flow

1️⃣ **User Registration/Login** → Create an account or log in.  
2️⃣ **Profile Setup** → Upload profile picture & set preferences.  
3️⃣ **Add Clothing Items** → Upload images, select category, size, and availability.  
4️⃣ **Browse & Search** → Explore available items by category or keyword.  
5️⃣ **Send Swap Offers** → Select an item, choose yours for exchange, and propose a location & time.  
6️⃣ **Offer Acceptance/Rejection** → Receive responses to swap requests.  
7️⃣ **Finalize Swap** → Meet at the agreed location and exchange items.  
8️⃣ **Swap History** → View past exchanges in your profile.

---

📂 Project Structure
📦 swapstyleproject
┣ 📂 adapters              # RecyclerView adapters for UI components
┣ 📂 data
┃ ┣ 📂 repository          # Firebase repository implementations
┃ ┣ 📂 model               # Data models for items, users, swaps
┣ 📂 ui
┃ ┣ 📂 fragments           # Home, Profile, AddItem, etc.
┃ ┣ 📂 activities          # Login, Register, Swap Offer, etc.
┣ 📂 utilities             # Helper classes (Image Picker, Animations, Location Utils)
┣ 📜 AndroidManifest.xml   # Application configuration & permissions
┣ 📜 build.gradle          # Dependencies & project configuration
┣ 📜 README.md             # Project documentation


## 🔧 Tech Stack

- **Programming Language:** Kotlin
- **UI Components:** Jetpack Compose, XML
- **Database:** Firebase Firestore
- **Authentication:** Firebase Auth
- **Storage:** Firebase Storage
- **Maps & Location:** Google Maps API
- **State Management:** LiveData, ViewModel
- **Asynchronous Operations:** Kotlin Coroutines


🎉 Getting Started
1️⃣ Clone the repository
git clone https://github.com/yourusername/swapstyle.git
2️⃣ Open in Android Studio
3️⃣ Connect Firebase project
4️⃣ Run the app on an emulator or device


