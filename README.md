# 🚀 SYNAPSE – College Management Information System (MIS)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-blue?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Backend-Firebase-orange?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Architecture-Role--Based-purple?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Status-Production%20Ready-success?style=for-the-badge"/>
</p>

---

## 📌 Overview

**SYNAPSE** is a **mobile-first College Management Information System (MIS)** built to replace fragmented tools and manual workflows with a **centralized, secure, and scalable platform**.

It enables seamless coordination between **Students, Faculty, HODs, and Examination Authorities (COE)** through a unified Android application.
<p>
  <a href=""><img src="https://img.shields.io/badge/Docs-Documentation-orange?style=for-the-badge"/></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/yatin-anchan/SYNAPSE-College-MIS/releases/tag/APP"><img src="https://img.shields.io/badge/Download-APK-blue?style=for-the-badge"/></a>
</p>

---

## 🎯 Problem Statement

Traditional college systems suffer from:

* ❌ Manual record-keeping (error-prone)
* ❌ Disconnected tools (Excel, standalone apps)
* ❌ No real-time data access
* ❌ Poor coordination across departments

---

## 💡 Solution

SYNAPSE provides:

* ✅ Centralized academic management
* ✅ Role-based secure access
* ✅ Automated workflows (attendance, exams, results)
* ✅ Real-time synchronization with offline support
* ✅ Scalable and modular architecture

---

## 🧠 Core Features

### 🔐 Authentication & Role-Based Access

* Multi-role login (Student, Faculty, HOD, COE)
* Secure Firebase Authentication
* Profile-based routing & validation

---

### 📊 Smart Dashboards

| Role          | Capabilities                                |
| ------------- | ------------------------------------------- |
| 👨‍🎓 Student | Attendance, marks, timetable, assignments   |
| 👩‍🏫 Faculty | Attendance marking, subjects, marks entry   |
| 🧑‍💼 HOD     | Department control, analytics, moderation   |
| 🏛️ COE       | Exam management, results, institution stats |

---

### 📝 Examination System

* Multi-step exam creation workflow
* Subject-level scheduling
* Marks entry (Written / Internal / Practical)
* HOD moderation layer
* COE publishing control
* Final result generation with CGPI
* PDF export (Result Gazette)

---

### 📅 Attendance & Analytics

* Real-time attendance tracking
* Defaulter detection (<75%)
* Advanced analytics:

  * 📊 Bar charts (date-wise)
  * 🥧 Pie charts (present vs absent)
  * 📈 Trends & insights
* Risk classification:

  * 🔴 At Risk
  * 🟡 Warning
  * 🟢 Good

---

### 🏫 Academic Management

* Course & semester creation
* Class and faculty assignment
* Department management
* Timetable builder (multi-day scheduling)

---

### 📡 Data & Performance

* Firebase Firestore (real-time DB)
* Offline caching support
* Secure role-based data access
* Optimized for mid-range Android devices

---

## 🏗️ System Architecture

```text
Android App (Kotlin)
        │
        ▼
Firebase Authentication
        │
        ▼
Cloud Firestore
        │
        ▼
Real-time Sync + Offline Cache
```

---

## 🛠️ Tech Stack

| Layer        | Technology              |
| ------------ | ----------------------- |
| Frontend     | Kotlin (Android Studio) |
| Backend      | Firebase                |
| Database     | Cloud Firestore         |
| Auth         | Firebase Authentication |
| UI/UX        | Material Design         |
| Architecture | Modular + Role-Based    |

---

## 📱 Screenshots

> *(Add real screenshots here for impact)*
## 📱 Screenshots

<p align="center">
  <table>
    <tr>
      <td align="center" style="padding:10px;">
        <img width="250" src="https://github.com/user-attachments/assets/6dc03009-b003-4cf6-9c17-785f59fa28a6"/><br/>
        <b>Login Screen</b>
      </td>
      <td align="center" style="padding:10px;">
        <img width="250" src="https://github.com/user-attachments/assets/971ead74-1d66-4590-af6e-26c3673515f6"/><br/>
        <b>COE Dashboard</b>
      </td>
      <td align="center" style="padding:10px;">
        <img width="250" src="https://github.com/user-attachments/assets/8994be57-bc7e-41de-ba36-7c836d183ba8"/><br/>
        <b>Attendance Analytics</b>
      </td>
    </tr>
  </table>
</p>


---

## ⚙️ Installation

```bash
git clone https://github.com/your-username/synapse-mis.git
cd synapse-mis
```

### Setup

1. Open in **Android Studio**
2. Add `google-services.json`
3. Sync Gradle
4. Run on Emulator / Device

---

## 🧪 Testing & Reliability

* ✔ Functional Testing (All modules)
* ✔ Integration Testing (Firebase sync)
* ✔ Role-based access validation
* ✔ Network failure handling
* ✔ Data consistency checks

---

## 🚀 Performance Highlights

* ⚡ Fast local processing (low latency)
* 📡 Efficient cloud sync
* 📱 Smooth performance on mid-range devices
* 🔄 Offline-first design

---

## ⚠️ Known Limitations

* Mobile-only (no web dashboard yet)
* Limited third-party integrations
* Firebase free-tier limits

---

## 🔮 Future Enhancements

* 🌐 Web Admin Panel
* 🤖 AI-based analytics & predictions
* 🔔 Push notifications
* 🏫 Multi-college support
* ☁️ Cloud backup & exports

---

## 📦 Release

**v1.0.0 – Initial Stable Release**

* Full MIS functionality
* Role-based system
* Exam & attendance modules
* Analytics dashboard

---

## 👨‍💻 Author

**Yatin Anchan**
B.Sc Computer Science
Royal College of Arts, Science & Commerce

---

## ⭐ Support

If this project helped you:

* ⭐ Star the repo
* 🍴 Fork it
* 🤝 Contribute

