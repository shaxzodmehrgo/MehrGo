# Mehrgo Driver — iOS SwiftUI Implementation Spec

> **Audience** — Claude Code, building a fresh iOS SwiftUI app that re-implements the Android driver app **Mehrgo Driver** (`uz.teamwork.mehrgodriver`).
>
> **Goal** — Ship a professional, user-friendly iOS driver app at feature parity with Mehrgo Driver Android **v1.9.1 (build 47)**. Backend, brand assets, API contracts, and WebSocket protocol are identical to Android. No half-implementations, no placeholder `TODO`s, no skipped screens. Every section below is a hard requirement.

---

## How Claude Code should read this spec

1. **Read `My iOS code style.md` first.** Every code rule in that file is mandatory here too — MVVM, `@MainActor` ViewModels, `VMState` enum, `Array<T>` syntax, `os_log` with `logTag`, file header block, `// MARK: -` conventions. This spec **never overrides** the code style; it only adds product, design, and feature requirements.
2. **Build in the order of §20 (Implementation Order).** Each milestone must compile and pass a smoke test before moving on. Don't jump ahead.
3. **Treat §17.0 (Design System) as the visual contract.** Every screen consumes tokens and components from §17.0 — never invent new colors, radii, or button styles per screen. If you need a new component, add it to §17.0.8 first.
4. **Treat §17.1–§17.26 as the functional contract.** Every screen listed must be implemented end-to-end, with all states (loading / empty / error / success / interrupted) handled.
5. **Use §10 (REST), §11 (WebSocket), §13 (APNs) as the integration contract.** Don't invent endpoints.
6. **Verify against §21 (Final Checklist) before declaring done.**

### Conventions used in this document

- **Must / Should / May** follow [RFC 2119](https://datatracker.ietf.org/doc/html/rfc2119) — *Must* is non-negotiable, *Should* allows a one-line justification if skipped, *May* is optional.
- Code blocks are normative — copy the structure verbatim and adapt naming.
- Tables marked **(authoritative)** override prose anywhere else in the doc.
- `§` references are clickable cross-references — use them when jumping between sections.

---

## Table of Contents

1. [Product Overview](#1-product-overview)
2. [Decisions Locked In](#2-decisions-locked-in)
3. [Xcode Project Setup](#3-xcode-project-setup)
4. [Folder Structure](#4-folder-structure)
5. [Brand Configuration (`Constants.swift`)](#5-brand-configuration-constantsswift)
6. [State Enums & Domain Constants](#6-state-enums--domain-constants)
7. [Persistence Layer](#7-persistence-layer)
8. [Models](#8-models)
9. [Networking — `DynamicClient`](#9-networking--dynamicclient)
10. [REST Endpoint Catalog](#10-rest-endpoint-catalog)
11. [WebSocket Manager](#11-websocket-manager)
12. [Location & Background Strategy (iOS adaptation)](#12-location--background-strategy-ios-adaptation)
13. [Push Notifications (APNs)](#13-push-notifications-apns)
14. [Yandex MapKit Integration](#14-yandex-mapkit-integration)
15. [External Map App Deep Links](#15-external-map-app-deep-links)
16. [Localization](#16-localization)
17. [Screen-by-Screen Specs](#17-screen-by-screen-specs)
    - [17.0 — Design System (visual contract)](#170--design-system-visual-contract)
        - [17.0.1 Design language & principles](#1701--design-language--principles)
        - [17.0.2 Color tokens (light + dark)](#1702--color-tokens-light--dark)
        - [17.0.3 Typography](#1703--typography)
        - [17.0.4 Spacing, layout & safe area](#1704--spacing-layout--safe-area)
        - [17.0.5 Corner radius, elevation & strokes](#1705--corner-radius-elevation--strokes)
        - [17.0.6 Iconography](#1706--iconography)
        - [17.0.7 Motion & haptics](#1707--motion--haptics)
        - [17.0.8 Core components](#1708--core-components)
        - [17.0.9 State patterns (loading / empty / error)](#1709--state-patterns-loading--empty--error)
        - [17.0.10 Navigation patterns](#17010--navigation-patterns)
        - [17.0.11 Accessibility](#17011--accessibility)
        - [17.0.12 Dark mode rules](#17012--dark-mode-rules)
    - [17.1 – 17.26 — Screens](#171--rootview-entry-router)
18. [Taximeter / Trip Calculation](#18-taximeter--trip-calculation)
19. [Error Handling Conventions](#19-error-handling-conventions)
20. [Implementation Order](#20-implementation-order)
21. [Final Checklist](#21-final-checklist)
22. [Appendix A — Repository / Use-Case Mirror](#appendix-a--repository--use-case-mirror-for-organization-reference)
23. [Appendix B — Open Questions / Known Unknowns](#appendix-b--open-questions--known-unknowns)
24. [Appendix C — Screen → Component Quick Reference](#appendix-c--screen--component-quick-reference)

---

## 1. Product Overview

**Mehrgo Driver** is the Android-equivalent driver companion app for the Mehrgo taxi service in Uzbekistan. Drivers use it to:

- Receive ride offers (both broadcast and private/personal) in real time via WebSocket
- Accept/skip offers within a short countdown (default 20 s)
- Run an in-trip taximeter that tracks distance, waiting time, and fare
- Navigate via Yandex Maps in-app or hand off to Google / Yandex / 2GIS / Waze
- View earnings, balance, order history, and notifications
- Top up balance via Click or PayMe
- Communicate with the dispatcher and client by phone

The Android app's source language is Russian/Uzbek/Kyrgyz/Kazakh; the iOS app must support all four.

### Active brand (locked in)

| Item | Value |
|---|---|
| App display name | **MehrGO Haydovchisi** (UZ default) / **MEHRGO TAXI** |
| Bundle ID | `uz.teamwork.mehrgodriver` |
| Version | `1.9.1` (build `47` — match Android start point) |
| REST base URL | `https://prod.mehrgo.uz/api/v1/` |
| Image base URL | `https://prod.mehrgo.uz` |
| WebSocket URL | `wss://prod.mehrgo.uz/socket` |
| Route API base URL | `https://route.teamwork.uz/` |
| Yandex MapKit API key | `2faf237f-7c43-4881-8f0d-78b577623220` |
| Phone number length | **13** (Uzbek, `+998XXXXXXXXX`) |
| Click merchant service ID | `35839` |
| Click merchant ID | `27682` |
| PayMe merchant ID | `66a1f85bd69d25572f43e273` |
| `WAITING_TIME_TURN_AUTO` | `false` |
| `MIN_WAITING_TIME_SINGLE` | `false` |

---

## 2. Decisions Locked In

These decisions are fixed for the iOS build — don't re-litigate them.

1. **Single brand** — Mehrgo only. No white-label scheme/target multiplexing in v1. The constants in §5 are hardcoded in `Constants.swift`.
2. **Map SDK** — **Yandex MapKit for iOS** (`YandexMapsMobile`). Same SDK family as Android; the API key carries over.
3. **Background model** — iOS-appropriate adaptation. Android's 3 foreground services don't translate; use the iOS equivalents in [§12](#12-location--background-strategy-ios-adaptation).
4. **Languages** — All four: `uz` (default), `kk`, `ky`, `ru`.
5. **Design language** — **iOS-native, HIG-aligned, brand-orange accent (`#F58320`).** Use SF Pro for typography, SF Symbols for icons, native presentation primitives (`NavigationStack`, `.sheet`, `.alert`, `.confirmationDialog`), and Dynamic Type. The look is *modern iOS*, **not** an Android port. Full token set and component library in [§17.0](#170--design-system-visual-contract).
6. **Architecture** — MVVM with `@MainActor` ViewModels (`*MVVM`) + SwiftUI Views (`*View`). No Combine pipelines beyond what `WebSocketManager` and `@Published` already provide. No third-party state-management library.
7. **Minimum iOS** — **iOS 16.0**. We rely on `NavigationStack`, `.scrollIndicators(.hidden)`, `.searchable(.navigationBarDrawer)`, `.toolbar(.hidden, for: .tabBar)`, and `.presentationDetents`.

---

## 3. Xcode Project Setup

### Project

- Project name: `MehrgoDriver`
- Display name: localized — `MehrGO Haydovchisi` / `MEHRGO TAXI` (RU)
- Bundle ID: `uz.teamwork.mehrgodriver`
- Deployment target: **iOS 16.0** (we use NavigationStack, `.task`, `.scrollIndicators`, `.searchable(.navigationBarDrawer)`)
- Interface: **SwiftUI**
- Language: **Swift 5.9+**
- Life cycle: **SwiftUI App**

### Capabilities

Enable in *Signing & Capabilities*:

- **Background Modes**: Location updates, Audio (for ringtone), Remote notifications, Background fetch, Background processing
- **Push Notifications** (APNs)
- **Maps** (for `MKMapKit` fallback if needed; primary map is Yandex)
- **Keychain Sharing** (for auth token)

### Info.plist keys

Add the following (use Russian-language descriptions; localize in `InfoPlist.strings`):

| Key | Value |
|---|---|
| `NSLocationAlwaysAndWhenInUseUsageDescription` | "We need your location to send ride offers and run the taximeter during trips, even when the app is in the background." |
| `NSLocationWhenInUseUsageDescription` | Same as above (shorter). |
| `NSLocationAlwaysUsageDescription` | Same (iOS 10 fallback string). |
| `NSCameraUsageDescription` | "Take photos for your driver profile and vehicle documents." |
| `NSPhotoLibraryUsageDescription` | "Select photos for your driver profile and vehicle documents." |
| `NSPhotoLibraryAddUsageDescription` | "Save trip receipts and screenshots." |
| `NSMicrophoneUsageDescription` | Only if you add a voice-chat feature later; otherwise omit. |
| `UIBackgroundModes` | `location`, `audio`, `remote-notification`, `fetch`, `processing` |
| `BGTaskSchedulerPermittedIdentifiers` | `uz.teamwork.mehrgodriver.location.upload`, `uz.teamwork.mehrgodriver.error.report` |
| `LSApplicationQueriesSchemes` | `comgooglemaps`, `yandexmaps`, `yandexnavi`, `dgis`, `waze`, `tel`, `sms`, `mailto` |
| `UIApplicationSupportsIndirectInputEvents` | `YES` |
| `ITSAppUsesNonExemptEncryption` | `NO` |
| `YMKApiKey` | `2faf237f-7c43-4881-8f0d-78b577623220` |
| `UISupportedInterfaceOrientations` | Portrait only (the driver always holds the phone vertically; this matches Android). |
| `UIStatusBarStyle` | `UIStatusBarStyleDefault` (we control per-screen via SwiftUI). |
| `CFBundleLocalizations` | `uz`, `kk`, `ky`, `ru` |
| `CFBundleDevelopmentRegion` | `uz` |
| `NSAppTransportSecurity` | leave default (`prod.mehrgo.uz` and `route.teamwork.uz` are HTTPS). |

### Dependencies (Swift Package Manager)

Add these via *File → Add Packages*:

| Library | URL | Purpose |
|---|---|---|
| **YandexMapsMobile** | `https://github.com/yandex-mobile/mapkit-ios` (or via CocoaPods if SPM unavailable — Yandex officially distributes via CocoaPods `YandexMapsMobile`, so a Podfile may be required) | In-app map |
| **Firebase iOS SDK** | `https://github.com/firebase/firebase-ios-sdk` | `FirebaseMessaging`, `FirebaseCrashlytics`, `FirebaseAnalytics` |
| **Kingfisher** | `https://github.com/onevcat/Kingfisher` | Async image loading (Android uses Glide) |
| **PhoneNumberKit** | `https://github.com/marmelroy/PhoneNumberKit` | Phone formatting/validation (replaces Android `Maskara`) |
| **DGCharts** | `https://github.com/ChartsOrg/Charts` | Earnings charts (replaces `MPAndroidChart`) |

> If SPM doesn't resolve Yandex MapKit, fall back to a Podfile with `pod 'YandexMapsMobile'` and use a hybrid SPM + CocoaPods setup. Document this clearly in the README you write.

### App entry

```swift
//
//  MehrgoDriverApp.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import os.log
import SwiftUI
import YandexMapsMobile
import FirebaseCore


@main
struct MehrgoDriverApp: App {
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    init() {
        FirebaseApp.configure()
        YMKMapKit.setApiKey(Constants.mapkitKey)
        YMKMapKit.setLocale(LanguageManager.shared.current.rawValue)
        YMKMapKit.sharedInstance()
        os_log(.info, "APP-MehrgoDriverApp: launched")
    }
    
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(ThemeManager.shared.preferredColorScheme)
                .environment(\.locale, LanguageManager.shared.locale)
        }
    }
}
```

`AppDelegate` handles APNs registration, FCM token forwarding, and push tap routing. See [§13](#13-push-notifications-apns).

---

## 4. Folder Structure

Follow exactly. Every file lives under `MehrgoDriver/`.

```
MehrgoDriver/
├── MehrgoDriverApp.swift
├── AppDelegate.swift
├── Assets.xcassets/
│   ├── AccentColor.colorset/        (Mehrgo brand orange; see §17.0)
│   ├── AppIcon.appiconset/
│   ├── BrandColors/                 (primary, secondary, surface, etc.)
│   └── Images/                      (logo, mapPlaceholder, carDefault, etc.)
├── Localization/
│   ├── uz.lproj/Localizable.strings
│   ├── kk.lproj/Localizable.strings
│   ├── ky.lproj/Localizable.strings
│   └── ru.lproj/Localizable.strings
├── Shared/
│   ├── Constants.swift              (§5)
│   ├── DynamicClient.swift          (§9)
│   ├── WebSocketManager.swift       (§11)
│   ├── LocationTracker.swift        (§12)
│   ├── PushNotificationCenter.swift (§13)
│   ├── KeychainStore.swift          (auth token only)
│   ├── HapticManager.swift
│   └── Extensions/
│       ├── String+Phone.swift
│       ├── Double+Currency.swift
│       ├── Date+Formatters.swift
│       └── View+Modifiers.swift     (.navigationBar, .errorAlert, etc.)
├── Utilities/
│   ├── AnimatedIcon.swift           (per code style §13)
│   ├── DevelopingView.swift         (placeholder for not-yet-built screens)
│   ├── LoadingView.swift
│   ├── PrimaryButton.swift          (filled Mehrgo orange)
│   ├── SecondaryButton.swift
│   ├── SlideToActButton.swift       (replaces SlideToAct on Android; UISwipeGesture-based)
│   ├── CountdownTimerView.swift     (used in OTP + auto-offer)
│   ├── OTPCodeField.swift           (4 digits, auto-advance)
│   ├── MaskedPhoneField.swift       (PhoneNumberKit-backed)
│   └── EmptyStateView.swift
├── Persistence/
│   ├── UserManager.swift            (§7)
│   ├── LanguageManager.swift
│   ├── ThemeManager.swift
│   ├── MapTypeManager.swift
│   ├── AppTypeManager.swift
│   ├── IntroduceManager.swift
│   ├── AccessPermissionsManager.swift
│   ├── FcmTokenManager.swift
│   ├── ErrorRequestStore.swift
│   ├── CoreDataStack.swift          (Core Data replaces Android Room)
│   └── MehrgoDriver.xcdatamodeld/   (Calculation entity)
├── MVVM/
│   ├── RootMVVM.swift               (cold-launch routing; §17.1)
│   ├── SplashMVVM.swift             (§17.2)
│   ├── LanguageMVVM.swift           (§17.3)
│   ├── IntroduceMVVM.swift          (§17.4)
│   ├── LoginMVVM.swift              (§17.5)
│   ├── SignUpMVVM.swift             (§17.6)
│   ├── SignUpVerifyMVVM.swift       (§17.7)
│   ├── PasswordRecoveryMVVM.swift   (§17.8)
│   ├── PasswordVerifyMVVM.swift     (§17.9)
│   ├── CompleteDriverInfoMVVM.swift (§17.10)
│   ├── HomeMVVM.swift               (top-level + tab state; §17.11)
│   ├── MapMVVM.swift                (driver map; §17.12)
│   ├── OrderOfferMVVM.swift         (incoming offer card; §17.13)
│   ├── TripMVVM.swift               (active trip / taximeter; §17.14 + §18)
│   ├── TripFinishMVVM.swift         (fare summary; §17.15)
│   ├── MyOrdersMVVM.swift           (§17.16)
│   ├── NotificationsMVVM.swift      (§17.17)
│   ├── SettingsMVVM.swift           (§17.18)
│   ├── ProfileMVVM.swift            (§17.19)
│   ├── BalanceMVVM.swift            (§17.20)
│   ├── HistoryMVVM.swift            (§17.21)
│   ├── EarningsMVVM.swift           (§17.22)
│   ├── SubscriptionsMVVM.swift      (§17.23)
│   ├── VideosMVVM.swift             (§17.24)
│   ├── ChooseMapMVVM.swift          (§17.25)
│   └── SupportMVVM.swift            (§17.26)
└── View/
    ├── Root/
    │   ├── RootView.swift
    │   ├── SplashView.swift
    │   ├── LanguageView.swift
    │   ├── IntroduceView.swift
    │   ├── ForceUpdateView.swift
    │   └── BlockedAppView.swift
    ├── Auth/
    │   ├── LoginView.swift
    │   ├── SignUpView.swift
    │   ├── SignUpVerifyView.swift
    │   ├── PasswordRecoveryView.swift
    │   ├── PasswordVerifyView.swift
    │   ├── PasswordChangeView.swift
    │   ├── CompleteDriverInfoView.swift
    │   ├── AccessPermissionsView.swift
    │   └── TermsOfUseView.swift
    └── Main/
        ├── MainView.swift                (TabView host)
        ├── Home/
        │   ├── MapHomeView.swift         (Navigator mode)
        │   ├── ListHomeView.swift        (List mode)
        │   ├── OrderOfferSheet.swift
        │   ├── TripView.swift
        │   ├── TripFinishView.swift
        │   ├── CallDispatcherSheet.swift
        │   └── CancelReasonSheet.swift
        ├── MyOrders/
        │   ├── MyOrdersView.swift
        │   ├── MyOrderRow.swift
        │   └── OrderHistoryDetailView.swift
        ├── Notifications/
        │   ├── NotificationsView.swift
        │   ├── NotificationRow.swift
        │   └── NotificationDetailView.swift
        ├── Settings/
        │   ├── SettingsView.swift
        │   ├── ProfileView.swift
        │   ├── BalanceView.swift
        │   ├── HistoryView.swift
        │   ├── EarningsView.swift
        │   ├── SubscriptionsView.swift
        │   ├── VideosView.swift
        │   ├── VideoPlayerView.swift
        │   ├── ChooseMapView.swift
        │   ├── ChooseAppTypeView.swift
        │   ├── ChooseLanguageView.swift
        │   ├── ChooseThemeView.swift
        │   └── SupportView.swift
        └── Common/
            ├── TabBarItem.swift
            ├── BalanceBadge.swift
            ├── OnlineToggle.swift
            └── NotificationBadge.swift
```

---

## 5. Brand Configuration (`Constants.swift`)

Hardcode the Mehrgo values. No `if BRAND == ...` branching.

```swift
//
//  Constants.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import Foundation


public enum Constants {
    
    // MARK: - URLs
    static let baseURL: String = "https://prod.mehrgo.uz/api/v1/"
    static let imageURL: String = "https://prod.mehrgo.uz"
    static let webSocketURL: String = "wss://prod.mehrgo.uz/socket"
    static let routeBaseURL: String = "https://route.teamwork.uz/"
    
    // MARK: - Map & Payment
    static let mapkitKey: String = "2faf237f-7c43-4881-8f0d-78b577623220"
    static let merchantServiceIdClick: Int = 35839
    static let merchantIdClick: Int = 27682
    static let merchantIdPayMe: String = "66a1f85bd69d25572f43e273"
    
    // MARK: - App
    static let appVersion: Int = 47
    static let appVersionName: String = "1.9.1"
    static let phoneNumberSize: Int = 13          // +998 + 9 digits
    static let verificationCodeSize: Int = 4
    static let passwordSize: Int = 5
    
    // MARK: - Timings
    static let waitTimeVerifyCode: Int = 60       // seconds before "Resend"
    static let defaultAcceptWaitTime: Int = 20    // seconds to accept an offer
    static let minimalTimeConnectSocket: TimeInterval = 10   // ping cadence
    static let timeDateInterval: TimeInterval = 0.05
    static let minimalSpeedKmh: Double = 7         // below this, waiting timer runs
    
    // MARK: - Taximeter behaviour
    static let waitingTimeTurnAuto: Bool = false
    static let minWaitingTimeSingle: Bool = false
    
    // MARK: - Blocked competitor apps (iOS uses LSApplicationQueriesSchemes only;
    //         iOS cannot enumerate other installed apps, so this list is informational.
    //         See §17.2 for the iOS approach.)
    static let blockedCompetitorBundleIdsInfo: Array<String> = [
        "uz.gotaxi.margilan.driver",
        "uz.bo1056.driver",
        "uz.onlinetaxi.driver",
        "uz.onlinetaxi.taxi1313.driver",
        "taximaster.tmtaxicaller.id3265",
        "ru.yandex.taximeter",
        "uz.promo.uzDriver",
        "uz.royaltaxi.driver",
        "ru.tmdriver.new"
    ]
    
    // MARK: - Socket message keys
    static let socketOrderNewPrivate: String = "order_new_for_nurse"
    static let socketOrderNew: String = "order_new"
    static let socketOrderAccepted: String = "order_accepted"
    static let socketOrderCancelled: String = "order_cancelled"
    static let socketOrderCancelledPrivate: String = "order_cancelled_for_nurse"
    static let socketNotificationNew: String = "notification_new"
    static let socketReceivePong: String = "pong"
    
    // MARK: - APNs payload keys (mirrors Android FCM keys)
    static let pushKeyDriverBonusCredited: String = "DRIVER_BONUS_CREDITED"
    static let pushKeyNewDriverNotification: String = "NEW_DRIVER_NOTIFICATION"
    
    // MARK: - Errors
    static let errorUnauthorized: String = "401"
}
```

---

## 6. State Enums & Domain Constants

All `Int`-coded states from Android's `Constants.kt`. Use Swift enums where the value is meaningful in code; keep the raw `Int` to match server responses.

```swift
//
//  DomainEnums.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import Foundation


// MARK: - Driver Account Status
public enum DriverStatus: Int, Codable {
    case userInfoDeleted = 1
    case userInfoCompleted = 5
    case verifyCodeConfirmed = 7
    case driverInfoCompleted = 9
    case driverActive = 10
    case driverTurnedNotActive = 11
}


// MARK: - Order Lifecycle (server-side `state`)
public enum OrderState: Int, Codable {
    case accepted = 2
    case started = 7
    case changedArrived = 8
    case changedGone = 9
}


// MARK: - Order History Status
public enum OrderHistoryStatus: Int, Codable {
    case cancelled = 10
    case accepted = 11
    case finished = 12
    case doing = 15
}


// MARK: - Order Originator
public enum OrderOriginator: Int, Codable {
    case driver = 1            // taximeter (driver-created)
    case manager = 3
    case dispatcher = 4
    case admin = 10
    case client = 25
}


// MARK: - Standard ViewModel state (per iOS code style)
public enum VMState {
    case Positive, Negative, Neutral, Loading
}


// MARK: - Theme
public enum AppTheme: String, CaseIterable, Identifiable {
    case Day = "day"
    case Night = "night"
    case System = "system"
    public var id: String { rawValue }
}


// MARK: - Language
public enum AppLanguage: String, CaseIterable, Identifiable {
    case Uzbek = "uz"
    case Kazakh = "kk"
    case Kyrgyz = "ky"
    case Russian = "ru"
    public var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .Uzbek:    return "O‘zbekcha"
        case .Kazakh:   return "Қазақша"
        case .Kyrgyz:   return "Кыргызча"
        case .Russian:  return "Русский"
        }
    }
}


// MARK: - App Type (driver UI mode)
public enum AppType: String, CaseIterable, Identifiable {
    case Navigator = "navigator"   // map-first
    case List = "list"             // order list-first
    public var id: String { rawValue }
}


// MARK: - External map app (for hand-off navigation)
public enum ExternalMap: String, CaseIterable, Identifiable {
    case Google = "google"
    case Yandex = "yandex"
    case YandexNavi = "yandex_navi"
    case TwoGis = "2gis"
    case Waze = "waze"
    public var id: String { rawValue }
}


// MARK: - Order action passed to /order/item
public enum OrderServiceAction: Int {
    case remove = 0
    case add = 1
}


// MARK: - Payment method
public enum PaymentMethod: String {
    case Click = "click"
    case PayMe = "pay_me"
}


// MARK: - Tab selection
public enum TabSelection: Int, CaseIterable, Identifiable {
    case Home = 0
    case MyOrders = 1
    case Notifications = 2
    case Settings = 3
    public var id: Int { rawValue }
    
    var titleKey: String {
        switch self {
        case .Home:          return "title_home"
        case .MyOrders:      return "title_my_orders"
        case .Notifications: return "title_notifications"
        case .Settings:      return "title_settings"
        }
    }
    
    var icon: String {
        switch self {
        case .Home:          return "map.fill"
        case .MyOrders:      return "list.bullet.rectangle.fill"
        case .Notifications: return "bell.fill"
        case .Settings:      return "gearshape.fill"
        }
    }
}
```

---

## 7. Persistence Layer

iOS-side equivalents of Android's `SharedPreferences` singletons. Each manager is a `final class` with `static let shared`, persisting via `UserDefaults` — **except `UserManager.authToken`, which lives in the Keychain**. Storage keys use kebab-case per iOS code style.

| Manager | Backing | Keys | Notes |
|---|---|---|---|
| `UserManager` | UserDefaults + Keychain | `user-data` (JSON of `User` model), `auth-token` (Keychain) | Token isolated for security. |
| `LanguageManager` | UserDefaults | `app-language`, `last-startup-language` | First launch → no value → show `LanguageView`. |
| `ThemeManager` | UserDefaults | `app-theme` | Default `.System`. Exposes `preferredColorScheme: ColorScheme?`. |
| `MapTypeManager` | UserDefaults | `external-map`, `external-map-chosen` | Default `.Google`. |
| `AppTypeManager` | UserDefaults | `app-type` | Default `.Navigator`. |
| `IntroduceManager` | UserDefaults | `introduce-completed` | `Bool`. Drives `LanguageView` → `IntroduceView` → auth gate. |
| `AccessPermissionsManager` | UserDefaults | `access-permissions-completed` | Tracks first-run permission flow. |
| `FcmTokenManager` | UserDefaults | `fcm-token`, `fcm-token-synced` | Token comes from `Messaging.messaging().fcmToken` (Firebase). |
| `ErrorRequestStore` | UserDefaults (JSON array) | `queued-error-reports` | Failed API call records for batch upload to `POST user/report`. |

### Manager template

```swift
//
//  LanguageManager.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import os.log
import Foundation


@MainActor
final class LanguageManager: ObservableObject {
    
    static let shared = LanguageManager()
    
    @Published private(set) var current: AppLanguage = .Uzbek
    
    private let storageKey: String = "app-language"
    private let logTag: String = "APP-LanguageManager"
    
    
    private init() {
        if let raw = UserDefaults.standard.string(forKey: storageKey),
           let lang = AppLanguage(rawValue: raw) {
            self.current = lang
            os_log(.info, "\(self.logTag): loaded \(raw)")
        } else {
            os_log(.info, "\(self.logTag): no language saved")
        }
    }
    
    
    var locale: Locale { Locale(identifier: current.rawValue) }
    var isSet: Bool { UserDefaults.standard.string(forKey: storageKey) != nil }
    
    
    func set(_ language: AppLanguage) {
        current = language
        UserDefaults.standard.set(language.rawValue, forKey: storageKey)
        os_log(.info, "\(self.logTag): saved \(language.rawValue)")
    }
}
```

Build the other managers identically — `@MainActor`, `ObservableObject`, `static let shared`, private storage key, `logTag`, single-purpose API.

### Core Data — `Calculation` (replaces Android Room)

Single entity, drives the taximeter when the app is killed and relaunched mid-trip.

| Attribute | Type | Role |
|---|---|---|
| `orderId` | `Int32` (indexed, unique) | Order this calculation belongs to. |
| `trackedTimeMs` | `Int64` | Driving duration in milliseconds. |
| `waitedTimeMs` | `Int64` | Total waiting duration. |
| `waitedTimeUntilGoneMs` | `Int64` | Wait time accrued **before** "Way" (trip start). |
| `locationsJSON` | `String` | Polyline as JSON `Array<{lat, lon, bearing, timestamp}>`. |
| `createdAt` | `Date` | For pruning stale rows. |

Wrap Core Data in a `CalculationStore` actor with these methods:

```swift
func upsert(orderId: Int) async
func get(orderId: Int) async -> CalculationDTO?
func appendLocations(orderId: Int, points: Array<TrackedPoint>) async
func setTrackedTime(orderId: Int, ms: Int64) async
func setWaitedTime(orderId: Int, ms: Int64) async
func setWaitedTimeUntilGone(orderId: Int, ms: Int64) async
func delete(orderId: Int) async
```

---

## 8. Models

All models live next to their consuming ViewModel in `MVVM/*MVVM.swift` files, **except** the truly shared ones below, which live in `Shared/Models/`.

### Shared models

```swift
//
//  SharedModels.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import Foundation


// MARK: - BaseResponse Wrapper
struct BaseResponse<T: Codable>: Codable {
    let data: T?
}


// MARK: - ErrorResponse
struct ErrorResponse: Codable {
    let status: Int?
    let message: String?
}


// MARK: - User
struct User: Codable, Identifiable {
    let id: Int
    let firstName: String?
    let fatherName: String?
    let lastName: String?
    let phone: String?
    let status: UserStatus?
    let authKey: String?
    let balance: Double?
    let branch: Branch?
    let today: TodayStats?
    let tariffs: Array<Tariff>?
    let orders: Array<Order>?
    let deviceToken: String?
    let workStatus: WorkStatus?
    let car: Car?
    
    enum CodingKeys: String, CodingKey {
        case id, phone, balance, branch, today, tariffs, orders, car
        case firstName = "first_name"
        case fatherName = "father_name"
        case lastName = "last_name"
        case status
        case authKey = "auth_key"
        case deviceToken = "device_token"
        case workStatus = "work_status"
    }
    
    var fullName: String {
        Array<String?>([lastName, firstName, fatherName])
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}


struct UserStatus: Codable {
    let valueText: String?
    let valueNumber: Int?
    
    enum CodingKeys: String, CodingKey {
        case valueText = "value_text"
        case valueNumber = "value_number"
    }
    
    var driverStatus: DriverStatus? {
        guard let n = valueNumber else { return nil }
        return DriverStatus(rawValue: n)
    }
}


struct TodayStats: Codable {
    let count: Int?
    let price: Double?
}


struct WorkStatus: Codable {
    let status: String?
}


// MARK: - Branch
struct Branch: Codable, Identifiable {
    let id: Int
    let name: String?
    let latitudeCity: Double?
    let longitudeCity: Double?
    let radiusCity: Double?
    let acceptWaiting: Int?
    let dispatcherNumber: String?
    let clientBonusSettings: ClientBonusSettings?
    let polygon: Polygon?
    let blockedApps: String?
    
    enum CodingKeys: String, CodingKey {
        case id, name, polygon
        case latitudeCity = "latitude_city"
        case longitudeCity = "longitude_city"
        case radiusCity = "radius_city"
        case acceptWaiting = "accept_waiting"
        case dispatcherNumber = "dispatcher_number"
        case clientBonusSettings = "client_bonus_settings"
        case blockedApps = "blocked_apps"
    }
}


struct ClientBonusSettings: Codable {
    let minAmount: Double?
    let maxAmount: Double?
    
    enum CodingKeys: String, CodingKey {
        case minAmount = "min_amount"
        case maxAmount = "max_amount"
    }
}


struct Polygon: Codable {
    let boundary: Array<Array<Double>>?
}


// MARK: - Order
struct Order: Codable, Identifiable {
    let id: Int
    let contact: Contact?
    let startingPrice: Double?
    let priceInCity: Double?
    let addressCategory: AddressCategory?
    let address: Address?
    let addressCategoryFinish: AddressCategory?
    let addressFinish: Address?
    let price: Double?
    let latitude: Double?
    let longitude: Double?
    let info: String?
    let status: OrderStatusName?
    var state: Int?           // mutable for lifecycle
    let addPrice: Double?
    let services: Array<OrderService>?
    let branch: Branch?
    let distance: Double?
    let locations: Array<OrderLocation>?
    let tariff: Tariff?
    let driverNumber: String?
    let car: Car?
    let from: Int?
    let useBonus: Bool?
    let promoCode: PromoCode?
    let isCardPayment: Bool?
    
    enum CodingKeys: String, CodingKey {
        case id, contact, address, price, latitude, longitude, info, status, state
        case services, branch, distance, locations, tariff, car, from
        case startingPrice = "starting_price"
        case priceInCity = "price_in_city"
        case addressCategory = "address_category"
        case addressCategoryFinish = "address_category_finish"
        case addressFinish = "address_finish"
        case addPrice = "add_price"
        case driverNumber = "driver_number"
        case useBonus = "use_bonus"
        case promoCode = "promo_code"
        case isCardPayment = "is_card_payment"
    }
}


struct Contact: Codable {
    let id: Int?
    let name: String?
    let phone: String?
    let bonus: Double?
}


struct OrderStatusName: Codable {
    let value: Int?
    let name: String?
}


struct Address: Codable, Identifiable {
    let id: Int
    let name: String?
    let latitude: Double?
    let longitude: Double?
}


struct AddressCategory: Codable, Identifiable {
    let id: Int
    let name: String?
}


struct OrderService: Codable, Identifiable {
    let id: Int
    let price: Double?
    let total: Double?
    let service: ServiceDetail?
}


struct ServiceDetail: Codable {
    let name: String?
    let info: String?
}


struct OrderLocation: Codable, Identifiable {
    let id: Int { Int("\(position ?? 0)\(Int(latitude ?? 0))") ?? 0 }
    let name: String?
    let position: Int?
    let latitude: Double?
    let longitude: Double?
}


struct Car: Codable {
    let carNumber: String?
    let carModel: CarModel?
    let carColor: CarColor?
    
    enum CodingKeys: String, CodingKey {
        case carNumber = "car_number"
        case carModel = "car_model"
        case carColor = "car_color"
    }
}


struct CarModel: Codable, Identifiable {
    let id: Int
    let name: String?
    let secondName: String?
    
    enum CodingKeys: String, CodingKey {
        case id, name
        case secondName = "second_name"
    }
}


struct CarColor: Codable, Identifiable {
    let id: Int
    let name: String?
    let secondName: String?
    
    enum CodingKeys: String, CodingKey {
        case id, name
        case secondName = "second_name"
    }
}


struct CarBrand: Codable, Identifiable {
    let id: Int
    let name: String?
}


// MARK: - Tariff
struct Tariff: Codable, Identifiable {
    let id: Int
    let name: String?
    let minDistance: Double?
    let distanceIntervals: Array<DistanceInterval>?
    let priceOfOut: Double?
    let minWaitTime: Int?
    let priceOfWaiting: Double?
    let minWaitTimeOnWay: Int?
    let priceOfWaitingOnWay: Double?
    let commission: Double?
    
    enum CodingKeys: String, CodingKey {
        case id, name, commission
        case minDistance = "min_distance"
        case distanceIntervals = "distance_intervals"
        case priceOfOut = "price_of_out"
        case minWaitTime = "min_wait_time"
        case priceOfWaiting = "price_of_waiting"
        case minWaitTimeOnWay = "min_wait_time_on_way"
        case priceOfWaitingOnWay = "price_of_waiting_on_way"
    }
}


struct DistanceInterval: Codable, Identifiable {
    let id: Int
    let start: Double?
    let end: Double?
    let price: Double?
}


struct PromoCode: Codable {
    let usage: PromoUsage?
}


struct PromoUsage: Codable {
    let code: String?
    let amount: Double?
}


// MARK: - Region (Branch reference)
struct Region: Codable, Identifiable {
    let id: Int
    let name: String?
}


// MARK: - SignUp & Recovery
struct SignUp: Codable {
    let authKey: String?
    let waitingTime: Int?
    
    enum CodingKeys: String, CodingKey {
        case authKey = "auth_key"
        case waitingTime = "waiting_time"
    }
}


struct PasswordRecovery: Codable {
    let authKeyVerify: String?
    
    enum CodingKeys: String, CodingKey {
        case authKeyVerify = "auth_key_verify"
    }
}


// MARK: - Order Cancel Reason
struct OrderCancelReason: Codable, Identifiable {
    let id: Int
    let name: String?
}


// MARK: - Order Address (pickup zone)
struct OrderAddress: Codable, Identifiable {
    let id: Int
    let name: String?
    let count: Int?
}


// MARK: - Address in Branch
struct AddressInBranch: Codable, Identifiable {
    let id: Int
    let name: String?
    let latitude: Double?
    let longitude: Double?
}


// MARK: - Notification & Instruction & Video
struct DriverNotification: Codable, Identifiable {
    let id: Int
    let title: String?
    let message: String?
    let text: String?
    let image: String?
    let createdAt: String?
    
    enum CodingKeys: String, CodingKey {
        case id, title, message, text, image
        case createdAt = "created_at"
    }
}


struct Instruction: Codable, Identifiable {
    let id: Int
    let title: String?
    let body: String?
}


struct Video: Codable, Identifiable {
    let id: Int
    let title: String?
    let url: String?
    let category: String?
}


// MARK: - Earnings
struct DriverEarningsSummary: Codable {
    let total: Double?
    let tripsCount: Int?
    let totalDistance: Double?
    let totalDuration: Int?
    let byDay: Array<EarningsDay>?
    let byTariff: Array<EarningsTariff>?
    
    enum CodingKeys: String, CodingKey {
        case total
        case tripsCount = "trips_count"
        case totalDistance = "total_distance"
        case totalDuration = "total_duration"
        case byDay = "by_day"
        case byTariff = "by_tariff"
    }
}


struct EarningsDay: Codable, Identifiable {
    let id: String { date ?? UUID().uuidString }
    let date: String?
    let amount: Double?
    let trips: Int?
}


struct EarningsTariff: Codable, Identifiable {
    let id: Int
    let name: String?
    let amount: Double?
    let trips: Int?
}


// MARK: - History
struct OrderHistoryResponse: Codable {
    let items: Array<OrderHistoryItem>?
    let _meta: PageMeta?
}


struct OrderHistoryItem: Codable, Identifiable {
    let id: Int
    let status: OrderStatusName?
    let date: HistoryDate?
    let myOrder: HistoryOrderRef?
    
    enum CodingKeys: String, CodingKey {
        case id, status, date
        case myOrder = "myOrder"
    }
}


struct HistoryDate: Codable {
    let datetime: String?
    let date: String?
    let time: String?
}


struct HistoryOrderRef: Codable {
    let id: Int?
    let price: Double?
    let addressCategory: AddressCategory?
    let address: Address?
    
    enum CodingKeys: String, CodingKey {
        case id, price, address
        case addressCategory = "address_category"
    }
}


struct PageMeta: Codable {
    let totalCount: Int?
    let pageCount: Int?
    let currentPage: Int?
    let perPage: Int?
}


// MARK: - Introduce
struct Introduce: Codable, Identifiable {
    let id: Int
    let title: String?
    let description: String?
    let image: String?
}


// MARK: - Terms
struct TermsOfUse: Codable {
    let content: String?
}


// MARK: - Update App
struct UpdateApp: Codable {
    let versionCode: Int?
    let versionName: String?
    let required: Bool?
    let blockedApps: String?
    
    enum CodingKeys: String, CodingKey {
        case versionName = "version_name"
        case versionCode = "version_code"
        case required
        case blockedApps = "blocked_apps"
    }
}


// MARK: - Device token registration
struct DeviceTokenRequest: Codable {
    let token: String
}


struct DeviceTokenResult: Codable {
    let success: Bool?
}


// MARK: - Subscriptions
struct SubscriptionData: Codable {
    let items: Array<SubscriptionPlan>?
    let _meta: PageMeta?
}


struct SubscriptionPlan: Codable, Identifiable {
    let id: Int
    let name: String?
    let price: Double?
    let duration: Int?
    let description: String?
}


// MARK: - Routing
struct DirectionLocations: Codable {
    let routes: Array<RouteEntry>?
}


struct RouteEntry: Codable {
    let geometry: String?
    let distance: Double?
    let duration: Double?
    let steps: Array<RouteStep>?
}


struct RouteStep: Codable {
    let distance: Double?
    let duration: Double?
    let geometry: String?
    let name: String?
}


// MARK: - Order finish request
struct RequestOrderFinish: Codable {
    let distance: String
    let latitudeFinish: Double?
    let longitudeFinish: Double?
    let totalPrice: String
    let waitingTime: String
    let executionTime: String
    let finishAddressId: Int?
    let bonusPayment: Int64
    let promoCodePayment: Int64
    
    enum CodingKeys: String, CodingKey {
        case distance
        case latitudeFinish = "latitude_finish"
        case longitudeFinish = "longitude_finish"
        case totalPrice = "total_price"
        case waitingTime = "waiting_time"
        case executionTime = "execution_time"
        case finishAddressId = "finish_address_id"
        case bonusPayment = "bonus_payment"
        case promoCodePayment = "promo_code_payment"
    }
}


// MARK: - Order create request (driver-created taximeter order)
struct OrderCreateRequest: Codable {
    let tariffId: Int
    let branchId: Int
    let info: String?
    let price: Double
    let startingPrice: Double
    let distance: Double
    let useBonus: Bool
    let services: Array<OrderCreateService>
    let locations: Array<OrderCreateLocation>
    
    enum CodingKeys: String, CodingKey {
        case info, price, distance, services, locations
        case tariffId = "tariff_id"
        case branchId = "branch_id"
        case startingPrice = "starting_price"
        case useBonus = "use_bonus"
    }
}


struct OrderCreateService: Codable {
    let id: Int
    let count: Int
    let value: Double?
}


struct OrderCreateLocation: Codable {
    let lat: Double
    let lon: Double
    let position: Int
    let name: String?
}


// MARK: - Error report (queued for /user/report)
struct QueuedErrorReport: Codable {
    let url: String
    let token: String
    let requestTime: String
    let responseTime: String
    let duration: Int64
    let socketStatus: Bool
    let responseCode: Int
    let responseBody: String
}
```

---

## 9. Networking — `DynamicClient`

Single client. Two base URLs (main + route). Uses `URLSession` with `async/await`. Adds `Accept-Language` and `Authorization: Bearer …` automatically. On non-2xx, captures a `QueuedErrorReport` and stores it in `ErrorRequestStore` for later upload via `POST user/report`.

### Implementation skeleton

```swift
//
//  DynamicClient.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import os.log
import Foundation


// MARK: - HTTP method
public enum HttpMethod: String {
    case GET, POST, PUT, DELETE, PATCH
}


// MARK: - Base
public enum ApiBase: String {
    case main, route
    var url: String {
        switch self {
        case .main:  return Constants.baseURL
        case .route: return Constants.routeBaseURL
        }
    }
}


// MARK: - Client errors
public enum ClientError: Error {
    case noConnection
    case unauthorized
    case server(message: String)
    case decoding(String)
    case unknown
}


// MARK: - DynamicClient
@MainActor
public final class DynamicClient {
    
    static let shared: DynamicClient = DynamicClient()
    
    private let session: URLSession
    private let logTag: String = "APP-DynamicClient"
    private let timeoutRequest: TimeInterval = 40
    private let timeoutResource: TimeInterval = 60
    
    
    private init() {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = timeoutRequest
        cfg.timeoutIntervalForResource = timeoutResource
        cfg.waitsForConnectivity = true
        self.session = URLSession(configuration: cfg)
    }
    
    
    // MARK: - Public — JSON request
    public func request<T: Decodable>(
        base: ApiBase = .main,
        path: String,
        method: HttpMethod,
        query: Array<URLQueryItem>? = nil,
        body: [String: Any]? = nil,
        formUrlEncoded: Bool = false,
        type: T.Type
    ) async throws -> T {
        let url = try buildURL(base: base, path: path, query: query)
        var req = URLRequest(url: url)
        req.httpMethod = method.rawValue
        applyHeaders(to: &req, formUrlEncoded: formUrlEncoded)
        if let body = body {
            req.httpBody = encodeBody(body, formUrlEncoded: formUrlEncoded)
        }
        
        let logMethod = "\(self.logTag) | \(method.rawValue) | \(path)"
        os_log(.info, "\(logMethod) | sending")
        
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else {
            os_log(.error, "\(logMethod) | invalid response")
            throw ClientError.unknown
        }
        
        if !(200..<300).contains(http.statusCode) {
            await queueError(url: url, code: http.statusCode, body: data)
            if http.statusCode == 401 { throw ClientError.unauthorized }
            let msg = (try? JSONDecoder().decode(ErrorResponse.self, from: data))?.message
            throw ClientError.server(message: msg ?? "Server error \(http.statusCode)")
        }
        
        do {
            let decoded = try JSONDecoder().decode(T.self, from: data)
            os_log(.info, "\(logMethod) | decoded")
            return decoded
        } catch {
            os_log(.error, "\(logMethod) | decode failed - \(error.localizedDescription)")
            throw ClientError.decoding(error.localizedDescription)
        }
    }
    
    
    // MARK: - Public — Multipart (driver upload)
    public func multipart<T: Decodable>(
        path: String,
        fields: [String: String],
        photo: Data?,
        photoFieldName: String,
        photos: Array<Data>?,
        photosFieldName: String,
        type: T.Type
    ) async throws -> T {
        // Build multipart body; same header rules apply.
        // Implementation: use a `MultipartBuilder` helper.
        // ...
        fatalError("Implement using MultipartBuilder; see §17.10")
    }
    
    
    // MARK: - Headers
    private func applyHeaders(to req: inout URLRequest, formUrlEncoded: Bool) {
        req.setValue(formUrlEncoded ? "application/x-www-form-urlencoded"
                                    : "application/json",
                     forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(LanguageManager.shared.current.rawValue,
                     forHTTPHeaderField: "Accept-Language")
        if let token = KeychainStore.shared.authToken, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
    }
    
    
    // MARK: - URL & body
    private func buildURL(base: ApiBase, path: String,
                          query: Array<URLQueryItem>?) throws -> URL {
        guard var comps = URLComponents(string: base.url + path) else {
            throw ClientError.unknown
        }
        if let q = query, !q.isEmpty { comps.queryItems = q }
        guard let url = comps.url else { throw ClientError.unknown }
        return url
    }
    
    
    private func encodeBody(_ body: [String: Any],
                            formUrlEncoded: Bool) -> Data? {
        if formUrlEncoded {
            return body.map { "\($0.key)=\(($0.value as? String) ?? "\($0.value)")" }
                .joined(separator: "&")
                .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)?
                .data(using: .utf8)
        }
        return try? JSONSerialization.data(withJSONObject: body)
    }
    
    
    // MARK: - Error queue
    private func queueError(url: URL, code: Int, body: Data) async {
        let report = QueuedErrorReport(
            url: url.absoluteString,
            token: KeychainStore.shared.authToken ?? "",
            requestTime: ISO8601DateFormatter().string(from: Date()),
            responseTime: ISO8601DateFormatter().string(from: Date()),
            duration: 0,
            socketStatus: WebSocketManager.shared.isConnected,
            responseCode: code,
            responseBody: String(data: body, encoding: .utf8) ?? ""
        )
        ErrorRequestStore.shared.append(report)
        os_log(.error, "\(self.logTag): queued error \(code)")
    }
}
```

### Usage pattern (per code style §9)

Always call from a `@MainActor` ViewModel, propagate state via `VMState`:

```swift
func loadUser() {
    guard state != .Loading else { return }
    state = .Loading
    
    Task {
        do {
            let res: BaseResponse<User> = try await DynamicClient.shared.request(
                path: "user/me", method: .GET, type: BaseResponse<User>.self
            )
            if let user = res.data {
                self.user = user
                self.state = .Positive
                UserManager.shared.save(user)
                os_log(.info, "\(self.logTag): user loaded")
            } else {
                self.state = .Negative
            }
        } catch ClientError.unauthorized {
            UserManager.shared.signOut()
            self.state = .Negative
        } catch {
            self.errorMessage = error.localizedDescription
            self.state = .Negative
            os_log(.error, "\(self.logTag): \(error.localizedDescription)")
        }
    }
}
```

---

## 10. REST Endpoint Catalog

> All endpoints below are **mounted at `Constants.baseURL`** unless marked **(route)**. Auth-required endpoints rely on `Authorization: Bearer <token>` injected by `DynamicClient`. Response shapes always come back as `BaseResponse<T>` unless noted.

### Auth & Onboarding

| # | Verb | Path | Body / Query | Response | Use Case |
|---|---|---|---|---|---|
| 1 | POST | `user/login` | form: `phone`, `password` | `BaseResponse<User>` | Sign in |
| 2 | POST | `user/register` | form: `phone`, `first_name`, `father_name`, `last_name`, `password`, `password_repeat` | `BaseResponse<SignUp>` | Start signup; returns `auth_key` |
| 3 | POST | `user/refresh` | form: `auth_key` | `BaseResponse<SignUp>` | Resend signup SMS |
| 4 | POST | `user/confirm` | form: `auth_key`, `code`, `device_token` | `BaseResponse<User>` | Verify signup SMS |
| 5 | POST | `user/recover` | form: `phone` | `BaseResponse<PasswordRecovery>` | Start password reset |
| 6 | POST | `user/refresh?recover=1` | form: `auth_key` | `BaseResponse<PasswordRecovery>` | Resend recovery SMS |
| 7 | POST | `user/change-password` | form: `password`, `password_repeat`, `code`, `auth_key` | `BaseResponse<User>` | Set new password |
| 8 | GET | `slider/index` | — | `BaseResponse<Array<Introduce>>` | Onboarding slides |
| 9 | GET | `license/index` | — | `BaseResponse<TermsOfUse>` | T&C text |
| 10 | GET | `branch/index` | — | `BaseResponse<Array<Region>>` | Regions list |
| 11 | GET | `cars/index?type=brand` | — | `BaseResponse<Array<CarBrand>>` | Brands |
| 12 | GET | `cars/index?type=model&brand_id={id}` | — | `BaseResponse<Array<CarModel>>` | Models for brand |
| 13 | GET | `cars/index?type=color` | — | `BaseResponse<Array<CarColor>>` | Colors |
| 14 | POST | `user/fill-data` | multipart (driver info; see §17.10) | `BaseResponse<User>` | Complete driver profile |

### Profile / App

| # | Verb | Path | Body / Query | Response |
|---|---|---|---|---|
| 15 | GET | `user/me` | — | `BaseResponse<User>` |
| 16 | GET | `mobile/version-driver?token=…&device_token=…` | query | `UpdateApp` (no wrapper) |
| 17 | POST | `device-token/register` | JSON `{token}` | `BaseResponse<DeviceTokenResult>` |
| 18 | GET | `user/change-language?language=…` | query | `BaseResponse<String>` |
| 19 | POST | `user/report` | JSON `Array<QueuedErrorReport>` | `BaseResponse<Any>` |
| 20 | POST | `driver/start` | form: `device_token` (optional) | `BaseResponse<Any>` |
| 21 | POST | `driver/end` | — | `BaseResponse<Any>` |

### Orders — Pool & Lifecycle

| # | Verb | Path | Body / Query | Response |
|---|---|---|---|---|
| 22 | GET | `order/new-address` | — | `BaseResponse<Array<OrderAddress>>` |
| 23 | GET | `order/index/{category_id}` | path | `BaseResponse<Array<Order>>` |
| 24 | GET | `order/list` | — | `BaseResponse<Array<Order>>` |
| 25 | POST | `order/accept?id={orderId}` | query | `BaseResponse<Order>` |
| 26 | GET | `order/order-skip?order_id={orderId}` | query | `BaseResponse<Any>` |
| 27 | GET | `order-cancel-issue?type=1` | query | `BaseResponse<Array<OrderCancelReason>>` |
| 28 | POST | `order/cancel` | form: `order_id`, `issue_id` | `BaseResponse<Any>` |
| 29 | GET | `order/start?order_id={orderId}` | query | `BaseResponse<Order>` |
| 30 | GET | `order-change/state?state=8&id={orderId}` | query | `BaseResponse<Order>` (driver arrived) |
| 31 | GET | `order-change/state?state=9&id={orderId}` | query | `BaseResponse<Order>` (driver went) |
| 32 | POST | `order/complete?order_id={orderId}` | JSON `RequestOrderFinish` | `BaseResponse<User>` |
| 33 | GET | `order/my-orders` | — | `BaseResponse<Array<Order>>` |
| 34 | GET | `order/my-private-orders` | — | `BaseResponse<Array<Order>>` |
| 35 | GET | `service/order?id={orderId}` | query | `BaseResponse<Array<OrderService>>` |
| 36 | POST | `order/item` | form: `order_id`, `service_id`, `action` (0/1) | `BaseResponse<Any>` |
| 37 | GET | `address?branch_id={branchId}` | query | `BaseResponse<Array<AddressInBranch>>` |
| 38 | POST | `order-new/create` | JSON `OrderCreateRequest` | `BaseResponse<Any>` |
| 39 | GET | `order/history?expand=myOrder&page={page}` | query | `BaseResponse<OrderHistoryResponse>` |

### Location / Earnings / Content

| # | Verb | Path | Body / Query | Response |
|---|---|---|---|---|
| 40 | POST | `location/send` | form: `lat`, `lon`, `bearing` | `BaseResponse<Any>` |
| 41 | GET | `driver-earnings/summary?period=…&from=…&to=…&tz=…` | query | `BaseResponse<DriverEarningsSummary>` |
| 42 | GET | `notification/index` | — | `BaseResponse<Array<DriverNotification>>` |
| 43 | GET | `instruction/index` | — | `BaseResponse<Array<Instruction>>` |
| 44 | GET | `video/index` | — | `BaseResponse<Array<Video>>` |
| 45 | GET | `tarif?expand=name_with_group&is_taximeter=1&branch_id={branchId}` | query | `BaseResponse<Array<Tariff>>` |
| 46 | GET | `subscription/list?page={page}` | query | `BaseResponse<SubscriptionData>` |
| 47 | GET | `subscription/purchase?id={id}` | query | `BaseResponse<Any>` |

### Route service (separate base URL)

| # | Verb | Path | Response |
|---|---|---|---|
| R1 | GET (route) | `route/v1/driving/{lon1},{lat1};{lon2},{lat2}?steps=true` | `DirectionLocations` |
| R2 | GET (route) | `route/v1/driving/{lon1},{lat1};{lon2},{lat2}` | `DirectionLocations` |

---

## 11. WebSocket Manager

The driver's live offer pipeline. Single connection, auto-reconnect, ping every 10 s. Publishes events via `Combine` `PassthroughSubject`s consumed by `MapMVVM` / `OrderOfferMVVM`.

### Connection contract

- URL: `wss://prod.mehrgo.uz/socket?token={authToken}`
- Heartbeat: send the literal string `"ping"` every `Constants.minimalTimeConnectSocket` (10 s). Server replies with `{"key":"pong"}`.
- If no pong arrives within the next interval, mark disconnected and reconnect.
- Close with code `1000` ("Work finished") when driver goes offline.

### Message envelope

```json
{ "status": 0, "key": "order_new", "data": { ... } }
```

### Handled keys

| `key` | Payload `data` | Action |
|---|---|---|
| `pong` | — | Mark socket healthy. |
| `notification_new` | `{ id, message, text }` | Emit `NotificationEvent`; show local notification when backgrounded. |
| `order_new` | `Order` | Emit `OrderEvent(.new, order)` — broadcast offer. |
| `order_new_for_nurse` | `Order` | Emit `OrderEvent(.newPrivate, order)` — private/recommended offer (priority). |
| `order_accepted` | `Order` | Emit `OrderEvent(.accepted, order)` — another driver took it; clear from UI. |
| `order_cancelled` | `Order` | Emit `OrderEvent(.cancelled, order)`. |
| `order_cancelled_for_nurse` | `Order` | Emit `OrderEvent(.cancelledPrivate, order)`. |

### Implementation skeleton

```swift
//
//  WebSocketManager.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import os.log
import Combine
import Foundation


// MARK: - Events
enum SocketOrderKind { case new, newPrivate, accepted, cancelled, cancelledPrivate }


struct OrderEvent { let kind: SocketOrderKind; let order: Order }
struct NotificationEvent { let id: Int; let message: String; let text: String }


// MARK: - Manager
@MainActor
final class WebSocketManager: NSObject, ObservableObject {
    
    static let shared = WebSocketManager()
    
    @Published private(set) var isConnected: Bool = false
    
    let orderSubject = PassthroughSubject<OrderEvent, Never>()
    let notificationSubject = PassthroughSubject<NotificationEvent, Never>()
    
    private var task: URLSessionWebSocketTask?
    private var pingTimer: Timer?
    private var pongReceived: Bool = false
    private let logTag: String = "APP-WebSocketManager"
    
    
    func connect() {
        guard let token = KeychainStore.shared.authToken else { return }
        guard task == nil else { return }
        
        let url = URL(string: "\(Constants.webSocketURL)?token=\(token)")!
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: .main)
        task = session.webSocketTask(with: url)
        task?.resume()
        receive()
        startPing()
        os_log(.info, "\(self.logTag): connecting")
    }
    
    
    func disconnect() {
        pingTimer?.invalidate()
        pingTimer = nil
        task?.cancel(with: .normalClosure, reason: "Work finished".data(using: .utf8))
        task = nil
        isConnected = false
        os_log(.info, "\(self.logTag): disconnected")
    }
    
    
    // MARK: - Private
    private func receive() {
        task?.receive { [weak self] result in
            guard let self else { return }
            Task { @MainActor in
                switch result {
                case .success(.string(let text)):
                    self.handle(text)
                case .success(.data(let data)):
                    if let s = String(data: data, encoding: .utf8) { self.handle(s) }
                case .failure(let err):
                    os_log(.error, "\(self.logTag): \(err.localizedDescription)")
                    self.scheduleReconnect()
                    return
                @unknown default: break
                }
                self.receive()
            }
        }
    }
    
    
    private func handle(_ text: String) {
        guard let data = text.data(using: .utf8),
              let env = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let key = env["key"] as? String else { return }
        
        switch key {
        case Constants.socketReceivePong:
            pongReceived = true
            isConnected = true
            
        case Constants.socketNotificationNew:
            if let d = env["data"] as? [String: Any],
               let id = d["id"] as? Int {
                notificationSubject.send(NotificationEvent(
                    id: id,
                    message: d["message"] as? String ?? "",
                    text: d["text"] as? String ?? ""
                ))
            }
            
        case Constants.socketOrderNew,
             Constants.socketOrderNewPrivate,
             Constants.socketOrderAccepted,
             Constants.socketOrderCancelled,
             Constants.socketOrderCancelledPrivate:
            if let d = env["data"], let orderData = try? JSONSerialization.data(withJSONObject: d),
               let order = try? JSONDecoder().decode(Order.self, from: orderData) {
                let kind: SocketOrderKind = {
                    switch key {
                    case Constants.socketOrderNew:                return .new
                    case Constants.socketOrderNewPrivate:         return .newPrivate
                    case Constants.socketOrderAccepted:           return .accepted
                    case Constants.socketOrderCancelled:          return .cancelled
                    default:                                      return .cancelledPrivate
                    }
                }()
                orderSubject.send(OrderEvent(kind: kind, order: order))
            }
            
        default: break
        }
    }
    
    
    private func startPing() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: Constants.minimalTimeConnectSocket,
                                         repeats: true) { [weak self] _ in
            Task { @MainActor in self?.ping() }
        }
    }
    
    
    private func ping() {
        pongReceived = false
        task?.send(.string("ping")) { [weak self] err in
            Task { @MainActor in
                if let err = err {
                    os_log(.error, "\(self?.logTag ?? ""): ping failed - \(err.localizedDescription)")
                    self?.scheduleReconnect()
                }
            }
        }
        // After one cycle, verify pong arrived
        DispatchQueue.main.asyncAfter(deadline: .now() + Constants.minimalTimeConnectSocket - 0.5) { [weak self] in
            Task { @MainActor in
                if self?.pongReceived == false { self?.scheduleReconnect() }
            }
        }
    }
    
    
    private func scheduleReconnect() {
        isConnected = false
        task?.cancel()
        task = nil
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
            Task { @MainActor in self?.connect() }
        }
    }
}


extension WebSocketManager: URLSessionWebSocketDelegate {
    nonisolated func urlSession(_ session: URLSession,
                                webSocketTask: URLSessionWebSocketTask,
                                didOpenWithProtocol protocol: String?) {
        Task { @MainActor in
            self.isConnected = true
            os_log(.info, "\(self.logTag): connected")
        }
    }
}
```

---

## 12. Location & Background Strategy (iOS adaptation)

> Android uses 3 foreground services (`MyTrackingService`, `AutoOfferService`, `WindowToAppService`). iOS doesn't allow draw-on-top overlays over other apps and limits arbitrary background WebSocket sessions. Replace as follows:

### Replacements

| Android | iOS replacement |
|---|---|
| `MyTrackingService` foreground location + WebSocket | `CLLocationManager` with `allowsBackgroundLocationUpdates = true`, `pausesLocationUpdatesAutomatically = false`, `activityType = .automotiveNavigation`. Use **standard location updates** while the driver is online; consider **significant location changes** while idle to conserve battery. Keep `URLSessionWebSocketTask` open while the app is foreground OR has active background-location session. |
| Offer delivery while app killed | Push the offer via **APNs silent + alert push** (`content-available: 1` + alert). Server already sends socket frames; mirror them to APNs when the driver hasn't ack'd within ~2 s. On push receipt, schedule a critical local notification with `UNNotificationSound.criticalSoundNamed` and a high-priority alert tone. Show a custom in-app sheet when the user taps. |
| `AutoOfferService` overlay over other apps | **Not possible on iOS.** Replace with: ① a full-screen critical local notification (with custom sound), and ② an `OrderOfferSheet` shown on top of `MapHomeView` when the app comes to foreground. The driver must open the app to accept. Document this UX clearly to the driver in onboarding (see §17.4). |
| `WindowToAppService` floating button | Not applicable — iOS shows the app icon in the system status bar (red pill) when location is active, which already invites the user back. |

### `LocationTracker` responsibilities

```swift
@MainActor
final class LocationTracker: NSObject, ObservableObject {
    static let shared = LocationTracker()
    
    @Published private(set) var lastLocation: CLLocation?
    @Published private(set) var speedKmh: Double = 0
    @Published private(set) var isTracking: Bool = false
    @Published private(set) var isWaiting: Bool = false
    @Published private(set) var trackedTimeMs: Int64 = 0
    @Published private(set) var waitedTimeMs: Int64 = 0
    @Published private(set) var waitedTimeUntilGoneMs: Int64 = 0
    @Published private(set) var polyline: Array<TrackedPoint> = []
    
    func requestPermission()                      // .authorizedAlways required
    func startWhileOnline()                       // significant location changes
    func startTripTracking(orderId: Int) async    // upgrade to continuous + Core Data persistence
    func markWayStarted()                         // freezes waitedTimeUntilGone
    func pauseWaiting()                           // for explicit pause buttons
    func resumeWaiting()
    func stopTripTracking() async                 // final flush to Core Data
    func uploadBatchToServer() async              // POST location/send for the most recent fix
}


struct TrackedPoint: Codable {
    let lat: Double; let lon: Double; let bearing: Double; let timestamp: Date
}
```

### Tick logic

- Driving timer accrues whenever `speedKmh ≥ minimalSpeedKmh` (7 km/h).
- Waiting timer accrues whenever `speedKmh < minimalSpeedKmh` **and** the trip is past "arrive" (state ≥ 8). Before "arrive", waiting accrues into `waitedTimeUntilGoneMs`.
- Persist `trackedTimeMs` every 10 s, `waitedTimeMs` every 5 s, and append every 5th location fix to `polyline` via `CalculationStore`.
- Upload one POST to `location/send` every 20 s while online (matches Android's "100 m / 20 s" cadence).

### Background tasks

Register two `BGTaskScheduler` identifiers in `AppDelegate`:

| Identifier | When | What it does |
|---|---|---|
| `uz.teamwork.mehrgodriver.location.upload` | App backgrounded, online | Flushes queued locations to server. |
| `uz.teamwork.mehrgodriver.error.report` | App backgrounded, queue non-empty | POSTs `ErrorRequestStore` queue. |

iOS will only run these opportunistically. Don't rely on them for offer delivery — that is the job of APNs.

---

## 13. Push Notifications (APNs)

### Setup

1. Enable Push Notifications + Background Modes (Remote notifications) in capabilities.
2. Use Firebase Cloud Messaging as the push provider so the same backend that addresses Android FCM tokens addresses iOS too.
   - `import FirebaseMessaging` in `AppDelegate`.
   - On `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`, forward to `Messaging.messaging().apnsToken`.
   - On `Messaging.messaging().token { token, error in … }`, send the FCM token to backend via `POST device-token/register`.

### Channels (categories) → behaviour

| Android channel | iOS category identifier | Behaviour |
|---|---|---|
| `tracking_channel` (foreground service) | — | Replaced by background location indicator. No notification posted. |
| `new_order_channel` | `NEW_ORDER` | Banner + default sound. |
| `new_private_order_channel` | `NEW_PRIVATE_ORDER` | Time-sensitive interruption level, custom sound `audio_private_order.caf` (bundle), red action button "Open". |
| `my_order_cancel_channel` | `CANCEL_MY_ORDER` | Banner. |
| `new_notification_channel` | `NEW_NOTIFICATION` | Banner + default sound. |

### Payload routing

`AppDelegate` parses `userInfo["key"]` on tap and routes via `RootMVVM`:

| `key` value | Action |
|---|---|
| `DRIVER_BONUS_CREDITED` | Open `EarningsView`, pass `balance_id`. |
| `NEW_DRIVER_NOTIFICATION` | Open `NotificationsView`, pre-select `notify_driver_id`. |
| (else) | Open `MainView` at the home tab. |

### Local notifications

When the WebSocket delivers `order_new_private` while the app is in foreground but the driver isn't looking, also post a local notification with the `NEW_PRIVATE_ORDER` category — so audible behaviour matches Android.

---

## 14. Yandex MapKit Integration

### Initialization

```swift
YMKMapKit.setApiKey(Constants.mapkitKey)
YMKMapKit.setLocale(LanguageManager.shared.current.rawValue)
YMKMapKit.sharedInstance()
```

Call once in `MehrgoDriverApp.init()` before any map view loads.

### Map view wrapping

Wrap `YMKMapView` in a `UIViewRepresentable`:

```swift
struct YandexMap: UIViewRepresentable {
    @Binding var camera: CameraPosition
    let placemarks: Array<MapPlacemark>
    let polyline: Array<CLLocationCoordinate2D>?
    let userLocationOn: Bool
    
    func makeUIView(context: Context) -> YMKMapView { ... }
    func updateUIView(_ uiView: YMKMapView, context: Context) { ... }
    func makeCoordinator() -> Coordinator { ... }
}


struct MapPlacemark: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let imageName: String          // SF Symbol or asset
    let tappable: Bool
}


struct CameraPosition: Equatable {
    var target: CLLocationCoordinate2D
    var zoom: Float = 14
    var azimuth: Float = 0
    var tilt: Float = 0
}
```

Drive the camera from `MapMVVM` so SwiftUI state is the source of truth.

### Required map features

- Driver puck (custom blue arrow icon, rotated by `bearing`).
- Pickup placemark (green pin).
- Drop-off placemark (red pin).
- Multi-stop route line in brand orange.
- Tap on placemark → bottom sheet with that location's address.

---

## 15. External Map App Deep Links

When the driver taps "Open in [external map]" inside an active trip, hand off using `UIApplication.shared.open(url)` with the URL below. Source app preference is `MapTypeManager.shared.current`.

| App | Scheme | URL template |
|---|---|---|
| Google Maps | `comgooglemaps://` | `comgooglemaps://?daddr={lat},{lon}&directionsmode=driving` |
| Yandex Maps | `yandexmaps://` | `yandexmaps://maps.yandex.ru/?rtext=~{lat},{lon}&rtt=auto` |
| Yandex Navi | `yandexnavi://` | `yandexnavi://build_route_on_map?lat_to={lat}&lon_to={lon}` |
| 2GIS | `dgis://` | `dgis://2gis.ru/routeSearch/rsType/car/to/{lon},{lat}` |
| Waze | `waze://` | `waze://?ll={lat},{lon}&navigate=yes` |

If the chosen app's scheme is not present (`UIApplication.shared.canOpenURL`), fall back to the Apple Maps URL `http://maps.apple.com/?daddr={lat},{lon}&dirflg=d`.

---

## 16. Localization

Use `Localizable.strings` with keys mirroring Android's `strings.xml`. The 311 string entries below cover the whole app — the iOS spec keeps the exact same keys so the same translation team can fill them in. Below is the canonical key list (sample — produce the same key in all four `.lproj` files).

### Critical keys (must exist on day one)

- App naming: `app_name`, `app_name_capital`
- Tabs: `title_home`, `title_my_orders`, `title_notifications`, `title_settings`
- Auth: `login`, `sign_up`, `forgot_password`, `phone_label`, `password_label`, `password_confirm`, `first_name`, `father_name`, `last_name`, `terms_of_use`, `accept_terms`, `change_password`, `verify_code`, `resend_code`, `resend_in_seconds`
- Driver setup: `complete_driver_info`, `birthday`, `gender`, `passport_number`, `passport_given_by`, `passport_given_date`, `license_number`, `license_type`, `region`, `car_brand`, `car_model`, `car_color`, `car_number`, `car_made`, `select_photo`, `select_documents`
- Order offer: `new_order`, `individual_order`, `accept`, `skip`, `to_pickup`, `to_destination`, `services`, `price_commission`, `km`, `metre`, `sum`, `client_distance`
- Trip: `driver_arrived`, `go_away`, `waiting`, `to_finish`, `time_track`, `waited_time`, `speed`, `your_fare`, `your_order_cancelled`, `you_have_order_running`, `you_have_two_order`
- Cancellation: `cancellation`, `select_reason`, `confirm_cancel`, `call_dispatcher`, `call_client`
- Settings: `theme`, `language`, `map_type`, `app_type`, `videos`, `subscriptions`, `support`, `about`, `logout`, `version`
- Errors: `error_no_connection`, `error_server`, `error_unauthorized`, `error_gps_off`, `error_required_field`, `error_phone_invalid`, `error_password_short`, `error_passwords_dont_match`, `error_blocked_app`, `error_update_required`
- General: `yes`, `no`, `cancel`, `ok`, `continue`, `back`, `save`, `delete`, `edit`, `not_showed`, `not_defined`, `loading`

### Switching at runtime

```swift
// LanguageManager publishes `.current`. RootView reads it as @StateObject and applies
// `.environment(\.locale, languageManager.locale)` at the top of the view hierarchy.
// All `Text(…)` calls use `LocalizedStringKey` — no manual `NSLocalizedString` calls.
```

Force-refresh the view tree when the user switches language:

```swift
@Published var refreshToken: UUID = UUID()

func set(_ language: AppLanguage) {
    LanguageManager.shared.set(language)
    YMKMapKit.setLocale(language.rawValue)
    refreshToken = UUID()
    // Also fire-and-forget POST user/change-language
}
```

`RootView` uses `.id(languageManager.refreshToken)` to rebuild.

---

## 17. Screen-by-Screen Specs

> Every screen below is a hard requirement. No screen may be skipped, stubbed, or marked as "TODO". Every state must be handled. Every button must do what's specified.
>
> Per code style §5–§6: each ViewModel uses `@MainActor`, has `@Published var state: VMState = .Neutral`, `logTag = "APP-{ClassName}"`, and `Array<T>` syntax.

### 17.0 — Design System (visual contract)

> **This section is authoritative.** Every screen in §17.1–§17.26 consumes tokens and components defined here. Do **not** invent new colors, radii, typography, or button styles per screen. If you need something new, add it here first, then reference it from the screen spec.
>
> All design tokens live in a single `DesignSystem.swift` file under `Shared/` so they're trivially diffable and theme-aware. The Asset Catalog (`Assets.xcassets/BrandColors`) holds the *Any/Dark* color sets the tokens point at.

#### 17.0.1 — Design language & principles

The Mehrgo Driver iOS app feels like a **modern, native iOS app with a single warm brand accent** — not a re-skinned Android port. The driver works long shifts at the wheel, often in bright sunlight or at night, often with one hand. Every visual choice serves that context.

| Principle | What it means in practice |
|---|---|
| **Calm by default, loud only for offers** | Neutral grays everywhere; brand orange reserved for the primary CTA and live offer attention. No rainbow color coding. |
| **Large hit targets** | All taps ≥ 44 × 44 pt. Primary CTAs are 56 pt tall. The driver may be wearing gloves. |
| **One primary action per screen** | A single `PrimaryButton`. Secondary actions are `SecondaryButton` (tinted) or text buttons. |
| **System-native chrome** | Use `NavigationStack`, `.sheet`, `.alert`, `.confirmationDialog`, `.searchable`. Don't build custom nav bars unless a screen has a map underneath (Home / Trip). |
| **Dynamic Type & Dark Mode are first-class** | Both must work end-to-end. No fixed pixel font sizes; no hardcoded `.white`/`.black`. |
| **Motion is functional, not decorative** | Transitions confirm a state change. No purely cosmetic animation. Honor `Reduce Motion`. |
| **Haptics confirm important events** | Offer arrival, accept, slide-to-act, sign-out — see §17.0.7. Don't tap-haptic every list row. |
| **Critical states are unmissable** | Incoming private offer → red ring, heavy haptic, critical sound, full-screen cover (not a swipe-down sheet). |

#### 17.0.2 — Color tokens (light + dark)

Define one Asset-Catalog color set per token with light + dark appearances. Reference everywhere via the `Color` extensions below — **never** hardcode hex values in views.

##### Brand

| Token | Light | Dark | Use |
|---|---|---|---|
| `brand.primary` | `#F58320` | `#F58320` | Primary CTA fill, online-toggle "go online", live offer accents. |
| `brand.primaryPressed` | `#DE6F11` | `#C56A1A` | Pressed/hover state of primary surfaces. |
| `brand.onPrimary` | `#FFFFFF` | `#FFFFFF` | Text/icons on brand-filled surfaces. |
| `brand.primarySoft` | `#FEEFE0` | `#3A2412` | Tinted "Secondary" buttons, selected pill backgrounds. |

##### Surface & background

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg.app` | `#F7F8FA` | `#0B0F14` | Screen background. |
| `bg.surface` | `#FFFFFF` | `#161B22` | Card / sheet / cell background. |
| `bg.surfaceAlt` | `#F2F4F7` | `#1F2630` | Secondary container (input row, nested card). |
| `bg.scrim` | `rgba(0,0,0,0.40)` | `rgba(0,0,0,0.55)` | Sheet/alert backdrop. |

##### Text

| Token | Light | Dark | Use |
|---|---|---|---|
| `text.primary` | `#0A0E14` | `#F2F4F7` | Body, headings. |
| `text.secondary` | `#5B6573` | `#B0B8C2` | Subtitles, captions, meta. |
| `text.tertiary` | `#98A2B3` | `#6F7785` | Placeholders, disabled labels. |
| `text.disabled` | `#C5CCD6` | `#4A515C` | Disabled control labels. |
| `text.onAccent` | `#FFFFFF` | `#FFFFFF` | Text on brand fills. |

##### Stroke & separator

| Token | Light | Dark | Use |
|---|---|---|---|
| `stroke.subtle` | `#E5E8EB` | `#2A3038` | Card outlines, dividers, hairlines. |
| `stroke.strong` | `#C5CCD6` | `#3F4751` | Selected input outline. |
| `stroke.focus` | `#F58320` | `#F58320` | Focused input outline (a11y). |

##### Semantic

| Token | Light | Dark | Use |
|---|---|---|---|
| `semantic.success` | `#16A34A` | `#4ADE80` | Online indicator, paid badge, "trip finished" success. |
| `semantic.warning` | `#F59E0B` | `#FBBF24` | Mild warnings (signal low, GPS imprecise). |
| `semantic.danger` | `#DC2626` | `#F87171` | Cancel, error, sign-out, private-offer ring. |
| `semantic.info` | `#2563EB` | `#60A5FA` | Informational badges, "new" tags. |

##### Map overlay colors

| Token | Color | Use |
|---|---|---|
| `map.routeLine` | `brand.primary` @ 90 % | Active trip route. |
| `map.pinPickup` | `semantic.success` | Pickup placemark. |
| `map.pinDropoff` | `semantic.danger` | Drop-off placemark. |
| `map.driverPuck` | `semantic.info` | User location arrow. |

##### Swift API

```swift
//
//  DesignSystem.swift
//  MehrgoDriver
//
//  Created by Umar on DD/MM/YY.
//


import SwiftUI


extension Color {
    
    // MARK: - Brand
    static let brandPrimary: Color = Color("brand.primary")
    static let brandPrimaryPressed: Color = Color("brand.primaryPressed")
    static let brandOnPrimary: Color = Color("brand.onPrimary")
    static let brandPrimarySoft: Color = Color("brand.primarySoft")
    
    // MARK: - Surface
    static let bgApp: Color = Color("bg.app")
    static let bgSurface: Color = Color("bg.surface")
    static let bgSurfaceAlt: Color = Color("bg.surfaceAlt")
    
    // MARK: - Text
    static let textPrimary: Color = Color("text.primary")
    static let textSecondary: Color = Color("text.secondary")
    static let textTertiary: Color = Color("text.tertiary")
    static let textDisabled: Color = Color("text.disabled")
    static let textOnAccent: Color = Color("text.onAccent")
    
    // MARK: - Stroke
    static let strokeSubtle: Color = Color("stroke.subtle")
    static let strokeStrong: Color = Color("stroke.strong")
    
    // MARK: - Semantic
    static let success: Color = Color("semantic.success")
    static let warning: Color = Color("semantic.warning")
    static let danger: Color = Color("semantic.danger")
    static let info: Color = Color("semantic.info")
}
```

#### 17.0.3 — Typography

- **Family** — SF Pro (system default; no custom font).
- **Dynamic Type** — every text style scales with the user's Content Size Category. Use `.font(.system(...))` *only* when the design is fixed (numerals on a balance pill, etc.) and `.dynamicTypeSize(.xSmall ... .accessibility3)` to clamp where overflow would break layout.
- **Line spacing** — leave default; only override with `.lineSpacing(4)` for multi-line body in cards.

| Style | Font | Weight | Size (default) | Use |
|---|---|---|---|---|
| `displayLarge` | SF Pro Rounded | Bold | 34 | Fare total on `TripFinishView`, balance hero. |
| `displayMedium` | SF Pro | Bold | 28 | Screen titles where there is no NavigationBar. |
| `title1` | SF Pro | Bold | 24 | Section headers inside a screen. |
| `title2` | SF Pro | Semibold | 20 | Card headlines. |
| `headline` | SF Pro | Semibold | 17 | List row primary text. |
| `body` | SF Pro | Regular | 17 | Default body copy. |
| `bodyEmph` | SF Pro | Semibold | 17 | Emphasized body / quoted price. |
| `callout` | SF Pro | Regular | 16 | Secondary body, sheet subtitles. |
| `subheadline` | SF Pro | Regular | 15 | Inline meta. |
| `footnote` | SF Pro | Regular | 13 | Captions, helper text under fields. |
| `caption` | SF Pro | Medium | 12 | Badge labels, small status text. |
| `monoNumeric` | SF Mono | Medium | 17 | Live taximeter readouts (distance, time, fare ticking). Use `.monospacedDigit()` so digits don't shift width. |

```swift
extension Font {
    static let displayLarge: Font = .system(size: 34, weight: .bold, design: .rounded)
    static let displayMedium: Font = .system(size: 28, weight: .bold)
    static let title1: Font = .system(size: 24, weight: .bold)
    static let title2: Font = .system(size: 20, weight: .semibold)
    static let headline17: Font = .system(size: 17, weight: .semibold)
    static let body17: Font = .system(size: 17, weight: .regular)
    static let bodyEmph: Font = .system(size: 17, weight: .semibold)
    static let callout16: Font = .system(size: 16, weight: .regular)
    static let subheadline15: Font = .system(size: 15, weight: .regular)
    static let footnote13: Font = .system(size: 13, weight: .regular)
    static let caption12: Font = .system(size: 12, weight: .medium)
    static let monoNumeric17: Font = .system(size: 17, weight: .medium, design: .monospaced)
}
```

#### 17.0.4 — Spacing, layout & safe area

**4-pt grid**. Use the named scale, never raw numbers.

```swift
enum Space {
    static let xxs: CGFloat = 4
    static let xs:  CGFloat = 8
    static let sm:  CGFloat = 12
    static let md:  CGFloat = 16   // default screen horizontal padding
    static let lg:  CGFloat = 20
    static let xl:  CGFloat = 24
    static let xxl: CGFloat = 32
    static let xxxl:CGFloat = 40
    static let huge:CGFloat = 56
}
```

##### Screen padding

- Default horizontal padding for content: **`Space.md` (16)**.
- Vertical breathing room between cards: **`Space.sm` (12)**.
- Inside a card: **`Space.md` (16)** all sides.
- Section-to-section vertical gap: **`Space.xl` (24)**.

##### Safe area

- Every screen wraps content in a `VStack` inside a `ScrollView` (when scrollable) and respects `safeAreaInsets`.
- Floating bottom bars (online toggle, slide-to-act, trip controls) use `.safeAreaInset(edge: .bottom)` — do **not** absolute-position with `Spacer()` hacks.
- Map screens overlay top + bottom floating cards using `.overlay(alignment: .top)` / `.overlay(alignment: .bottom)`, with internal padding from `safeAreaInsets`.

##### Touch targets

- **Minimum 44 × 44 pt** (HIG). For icon-only buttons, wrap the icon in a 44 × 44 frame even if the glyph is smaller.
- Primary CTA: **56 pt tall**, full width minus 16 pt h-padding.
- Slide-to-act: **64 pt tall** (the thumb travels the full width).

#### 17.0.5 — Corner radius, elevation & strokes

```swift
enum Radius {
    static let xs: CGFloat = 6      // chips, tiny tags
    static let sm: CGFloat = 10     // text fields
    static let md: CGFloat = 14     // buttons
    static let lg: CGFloat = 16     // cards
    static let xl: CGFloat = 20     // large cards, modal sheets
    static let pill: CGFloat = 999  // pills, capsule badges
}
```

##### Elevation

Light mode uses subtle drop shadows. Dark mode replaces shadow with `bg.surfaceAlt` so cards remain visible without a black halo.

```swift
enum Elevation {
    static func card(_ scheme: ColorScheme) -> (color: Color, radius: CGFloat, y: CGFloat) {
        switch scheme {
        case .dark:  return (.clear, 0, 0)
        default:     return (.black.opacity(0.06), 12, 4)
        }
    }
}


extension View {
    func cardStyle(scheme: ColorScheme) -> some View {
        let e = Elevation.card(scheme)
        return self
            .background(Color.bgSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
            .shadow(color: e.color, radius: e.radius, x: 0, y: e.y)
    }
}
```

##### Strokes

- Cards in **dark mode** get a 1 pt `stroke.subtle` outline to separate from the background.
- Input fields use a 1 pt `stroke.subtle` border that switches to `stroke.focus` when focused.
- Selected sheet rows get a 1 pt `brand.primary` outline + filled `brand.primarySoft` background.

#### 17.0.6 — Iconography

- **Primary set: SF Symbols** (`Image(systemName:)`). Use semantic glyphs — `bell`, `gearshape`, `map`, `phone`, `person.crop.circle`, `car`, `creditcard`, `location.fill`.
- **Weight** — default `.regular` for body icons, `.semibold` for tab bar selected, `.bold` for status alerts.
- **Size** — match the surrounding text via `.imageScale(.medium)`. Don't hardcode pt sizes unless the icon is a hero glyph.
- **Brand glyphs** (Mehrgo logo, taximeter, driver puck arrow) live as PDF/SVG assets in `Assets.xcassets/Images`. Provide `Single Scale` PDF so Xcode renders crisp at every size.

##### Icon-on-circle pattern (used in lists, profile, settings)

```swift
struct IconBadge: View {
    let systemName: String
    let tint: Color
    var body: some View {
        Image(systemName: systemName)
            .imageScale(.medium)
            .foregroundStyle(tint)
            .frame(width: 32, height: 32)
            .background(tint.opacity(0.12))
            .clipShape(Circle())
    }
}
```

#### 17.0.7 — Motion & haptics

##### Durations

| Token | ms | When |
|---|---|---|
| `motion.micro` | 120 | Toggle on/off, color tint changes |
| `motion.short` | 200 | Button press, opacity fade |
| `motion.standard` | 300 | Sheet present/dismiss, nav push/pop |
| `motion.long` | 450 | Map zoom-to-fit, big layout reflows |

##### Easing

- Default: `.easeInOut(duration: motion.short)`.
- Spring (preferred for layout): `.interactiveSpring(response: 0.35, dampingFraction: 0.85)`.
- Always wrap user-perceivable animations in:
  ```swift
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  withAnimation(reduceMotion ? .none : .interactiveSpring(response: 0.35, dampingFraction: 0.85)) { ... }
  ```

##### Haptics

Wrap in a `HapticManager` singleton (already listed in §4). Don't sprinkle `UIImpactFeedbackGenerator` calls across views.

| Trigger | Haptic |
|---|---|
| Tab change | `.selectionChanged()` |
| Primary button tap | `.impact(.medium)` |
| Online toggle (off → on) | `.notification(.success)` |
| Online toggle (on → off) | `.impact(.light)` |
| Incoming offer (broadcast) | `.impact(.medium)` |
| Incoming **private** offer | `.impact(.heavy)` + repeated 3× over 600 ms |
| Slide-to-act crosses threshold | `.impact(.rigid)` |
| Slide-to-act success | `.notification(.success)` |
| Cancel order confirmation | `.notification(.warning)` |
| Validation error on submit | `.notification(.error)` |
| Sign-out confirmed | `.impact(.heavy)` |

Do **not** haptic-tap list rows, segment changes, or scroll edges.

#### 17.0.8 — Core components

All reusable UI lives under `Utilities/`. Each component has fixed sizing tokens and a single public API. Build these first (M1–M2) so the screen layer can compose freely.

##### `PrimaryButton`

The only filled-orange button in the app. One per screen.

```swift
struct PrimaryButton: View {
    
    let title: LocalizedStringKey
    var systemImage: String? = nil
    var isLoading: Bool = false
    var isEnabled: Bool = true
    let action: () -> Void
    
    var body: some View {
        Button(action: { HapticManager.shared.impact(.medium); action() }) {
            ZStack {
                if isLoading {
                    ProgressView().tint(.brandOnPrimary)
                } else {
                    HStack(spacing: Space.xs) {
                        if let systemImage { Image(systemName: systemImage) }
                        Text(title).font(.headline17)
                    }
                }
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .foregroundStyle(Color.brandOnPrimary)
            .background(isEnabled ? Color.brandPrimary : Color.textDisabled)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        }
        .disabled(!isEnabled || isLoading)
        .padding(.horizontal, Space.md)
        .accessibilityLabel(title)
        .accessibilityAddTraits(.isButton)
    }
}
```

##### `SecondaryButton`

Tinted (orange-on-soft-orange) for "Cancel", "Resend code", "Skip", etc.

- Background: `brand.primarySoft`. Text: `brand.primary`. Otherwise identical structure to `PrimaryButton`.

##### `TextButton`

Plain text link, e.g. "Forgot password". 17pt semibold, `brand.primary`, no background.

##### `MaskedPhoneField`

- Always rendered with `+998` prefix label, then `## ### ## ##` mask.
- 56 pt tall, `Radius.sm` corner, `stroke.subtle` outline, focus → `stroke.focus`.
- `keyboardType(.numberPad)` and `textContentType(.telephoneNumber)`.
- VoiceOver label: "Phone number, country code Uzbekistan plus nine nine eight".

##### `SecureField` (password)

- Eye toggle on trailing side using `Image(systemName: isVisible ? "eye.slash" : "eye")`.
- `textContentType(.password)` / `.newPassword` as appropriate.

##### `OTPCodeField`

- 4 boxes, 56 × 64 pt, 12 pt spacing, `Radius.sm` corners.
- Each box shows one digit; auto-advance focus.
- On full code → auto-submit (firing the closure).
- Backspace moves to previous box.
- `.notification(.error)` haptic + shake animation on rejection.

##### `Card`

```swift
struct Card<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: Space.sm, content: content)
            .padding(Space.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .cardStyle(scheme: scheme)
            .overlay(
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .stroke(scheme == .dark ? Color.strokeSubtle : .clear, lineWidth: 1)
            )
    }
}
```

##### `Pill`

- Capsule, height 28, padding 12 h × 6 v, `caption` font.
- Variants: `.neutral` (`bg.surfaceAlt`), `.success`, `.warning`, `.danger`, `.info`, `.brand` (`brand.primarySoft` + `brand.primary` text).
- Used for: order status, "TOP" badges, payment-method tags, "private offer" tag.

##### `Toggle` (online switch)

- Use the **system** `Toggle` everywhere *except* the home online toggle, which is a custom **`OnlineToggle`** (full-width capsule, label inside, fills `success` when on and `brand.primary` when off — see §17.0.8 component list and §17.12).

##### `SlideToActButton`

Replaces Android's `SlideToAct`. Specs:

- Track: 64 pt tall, `Radius.pill` corner, `brand.primarySoft` background, `bodyEmph` label centered ("Slide to arrive at pickup").
- Thumb: 56 × 56 pt circle, `brand.primary` fill, `arrow.right` SF Symbol icon, follows finger with `.interactiveSpring`.
- At 80 % travel the thumb snaps to the end and the success closure fires with `.notification(.success)` haptic.
- On release before 80 %, thumb springs back; light haptic.

##### `CountdownRing`

- Used inside `OrderOfferSheet` and OTP resend.
- 96 × 96 pt circle, 8 pt stroke, sweeps from `brand.primary` (full) to `semantic.danger` in the last 5 s.
- Center label: remaining seconds in `displayMedium`.

##### `BalanceBadge`

- Pill, 36 pt tall, padding 16 h × 8 v.
- Background: `brand.primary`. Text: `brand.onPrimary`, `headline17`, monospaced digits.
- Format: `1 234 567 sum` (thousand separator from `Locale.current`).

##### `NotificationBadge`

- 20 × 20 pt circle, `semantic.danger` fill, white digit, top-right of bell icon.
- Hidden when count is 0.

##### `EmptyStateView`

```swift
struct EmptyStateView: View {
    let systemImage: String
    let title: LocalizedStringKey
    let message: LocalizedStringKey
    var actionTitle: LocalizedStringKey? = nil
    var action: (() -> Void)? = nil
    var body: some View {
        VStack(spacing: Space.md) {
            Image(systemName: systemImage)
                .font(.system(size: 56, weight: .light))
                .foregroundStyle(Color.textTertiary)
            Text(title).font(.title2).foregroundStyle(Color.textPrimary)
            Text(message).font(.callout16).foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
            if let actionTitle, let action {
                PrimaryButton(title: actionTitle, action: action)
                    .padding(.top, Space.sm)
            }
        }
        .padding(.horizontal, Space.xl)
    }
}
```

##### `LoadingShimmer`

- Use for list-row skeletons during the first load of `MyOrdersView`, `NotificationsView`, `EarningsView`.
- Rectangle, `bg.surfaceAlt`, animated gradient sweep every 1.4 s. Respects Reduce Motion (falls back to plain pulse).

##### `ErrorBanner` / `Toast`

- Top toast: 56 pt tall, `Radius.md`, `semantic.danger` bg for errors / `bg.surfaceAlt` bg for info.
- Auto-dismiss after 3 s. Tap to dismiss early.
- Use for `error_no_connection`, `error_server`, generic API failures.

#### 17.0.9 — State patterns (loading / empty / error)

Every list/data screen has four canonical states. Build them all up front.

| State | UI |
|---|---|
| **Loading (first-time)** | Full-screen `ProgressView` for screens with no skeleton support, **or** 6 × `LoadingShimmer` rows for list screens. |
| **Loading (refresh)** | Native `.refreshable` spinner only — never replace the existing content. |
| **Empty** | `EmptyStateView` with an SF Symbol illustration, title, message, optional CTA. |
| **Error** | `EmptyStateView` with `exclamationmark.triangle` symbol + "Try again" button. Inline toast for transient errors. |
| **Success** | Render data. Use `.task { await load() }` so the load fires once per appearance. |

Map view state to `VMState`:

```swift
switch viewModel.state {
case .Loading where viewModel.items.isEmpty: SkeletonList()
case .Negative where viewModel.items.isEmpty: EmptyStateView(systemImage: "exclamationmark.triangle", ...)
case .Positive where viewModel.items.isEmpty: EmptyStateView(systemImage: "tray", ...)
default: itemList
}
```

#### 17.0.10 — Navigation patterns

- **Top-level**: `NavigationStack` per tab.
- **Push**: pass an `enum Route` value to a `NavigationLink(value:)`; resolve in `navigationDestination(for:)`. No string-based routes.
- **Sheet**: `.sheet(item:)` for data-driven sheets; `.sheet(isPresented:)` only for parameterless ones.
- **Detents** — use `.presentationDetents([.medium, .large])` for half-sheets (cancel reason, top-up amount). Pass `.large` only when the sheet is the focus (offer card with countdown).
- **Full-screen cover** — reserved for: incoming **private** offer, force-update modal, signup multi-step flow.
- **Alerts** — destructive confirmations only (sign-out, cancel order without reason). Two buttons max.
- **Confirmation dialogs** — short multi-choice (e.g. "Camera / Photo Library"). Use `.confirmationDialog`.
- **Back navigation** — the system `< Back` chevron is canonical. Don't add custom back buttons unless the screen has a map underneath (Trip).
- **Toolbar** — use `.toolbar { ToolbarItem(placement: .topBarTrailing) { ... } }`. Place primary action on trailing, secondary on leading.

#### 17.0.11 — Accessibility

This is a driver app — accessibility lapses translate directly to road-safety incidents. Take it seriously.

| Concern | Rule |
|---|---|
| Hit targets | ≥ 44 × 44 pt. Verify with Accessibility Inspector. |
| Dynamic Type | All `Text` uses `LocalizedStringKey` + `.font(.body17)` (or scale). Test at Default + AX3. Single-line layouts that would clip use `.lineLimit(1)` + `.minimumScaleFactor(0.85)`. |
| Color contrast | All text-on-background pairs meet WCAG AA (4.5:1 body, 3:1 large). The brand orange CTA passes only with white text — verify with the Accessibility Inspector. |
| VoiceOver labels | Every interactive element has `.accessibilityLabel(...)`. Icon-only buttons MUST have a label. Compound rows use `.accessibilityElement(children: .combine)`. |
| Live regions | When fare ticks in `TripView`, use `.accessibilityAddTraits(.updatesFrequently)` so VoiceOver doesn't interrupt. The offer countdown announces seconds remaining every 5 s, not every second. |
| Reduce Motion | Wrap all `withAnimation { ... }` in the `accessibilityReduceMotion` check. Disable shimmer and slide-to-act animations when reduced; use a tap-confirm fallback. |
| Reduce Transparency | Honor by switching scrims to opaque `bg.scrim` color when set. |
| Increase Contrast | Honor by switching `stroke.subtle` → `stroke.strong` everywhere. |
| Smart Invert / Display Filters | Mark photos/map images with `.accessibilityIgnoresInvertColors()` so they don't render as negatives. |
| Right-to-left | Uzbek/Kazakh/Kyrgyz/Russian are LTR — no RTL work needed, but use `.leading` / `.trailing` alignment everywhere instead of `.left` / `.right`. |

#### 17.0.12 — Dark mode rules

- **Every screen must work in dark mode.** Test by switching `Settings → Theme → Night` in-app and via system override.
- The app's `ThemeManager.preferredColorScheme` is wired at the `WindowGroup` root via `.preferredColorScheme(...)`.
- **Never** hardcode `.white`, `.black`, or a literal hex. Use tokens from §17.0.2.
- Map tiles: when `colorScheme == .dark`, switch Yandex map style to night mode via `YMKMap.set(theme: .dark)`.
- Status bar style: derive from the visible top surface — orange/dark headers → light content; everything else → automatic.
- Screenshots in the App Store listing must include at least one dark-mode shot per language.

### 17.1 — `RootView` (entry router)

**Purpose**: First view shown. Routes user based on persisted state.

**State machine** (delegate to `RootMVVM`):

```
launch
  └─ if !LanguageManager.isSet              → LanguageView
  └─ else if !IntroduceManager.completed    → IntroduceView
  └─ else if KeychainStore.authToken == nil → LoginView (inside NavigationStack)
  └─ else                                   → SplashView (which does version + me)
```

After splash finishes:

```
  └─ if forceUpdate                                → ForceUpdateView (modal)
  └─ else if driverStatus == .verifyCodeConfirmed  → CompleteDriverInfoView
  └─ else                                          → MainView (TabView)
```

Use `NavigationStack` at the top; route via a single `@Published var route: RootRoute` enum on `RootMVVM`.

### 17.2 — `SplashView` + `SplashMVVM`

**API calls (in order)**:

1. `GET mobile/version-driver?token={fcm}&device_token={apns}` → `UpdateApp`
2. If `versionCode > Constants.appVersion`: present `ForceUpdateView`. Skip button hidden if `required == true`.
3. If `blockedApps` non-empty: present `BlockedAppView` (informational only — iOS cannot enforce uninstall; just warn).
4. `GET user/me` → on success store in `UserManager`, navigate to `MainView`. On 401, sign out, show `LoginView`.

**UI**: centered Mehrgo logo (200 pt), 1.5 s minimum dwell, `AnimatedIcon`-style pulse, no progress bar.

**Force-update modal**: full-screen, app icon, title "New version available", body localized message, primary button "Update" → opens `https://apps.apple.com/app/idXXXXXXXXX` (TODO: insert real App Store ID after first TestFlight submission), secondary "Later" if not required.

### 17.3 — `LanguageView` + `LanguageMVVM`

- Shown only on first launch (or until user picks).
- 4 rows: O‘zbekcha, Қазақша, Кыргызча, Русский.
- Selection persists via `LanguageManager.set(_:)`, then navigates to `IntroduceView`.

### 17.4 — `IntroduceView` + `IntroduceMVVM`

- Fetches `GET slider/index`.
- TabView with `.tabViewStyle(.page)` to swipe slides.
- Each slide: image (from `Constants.imageURL + image`), title, description.
- Buttons: "Skip" (hidden on last slide), "Next" / "Start" (last slide).
- "Start" → `IntroduceManager.set(true)` → `LoginView`.
- Also include 1 iOS-specific slide near the end: **"Keep notifications enabled and grant Always-On location so you receive offers even with the app in the background. iOS does not allow showing offers on top of other apps — you'll get a banner and a sound; tap to open."** (Translated.)

### 17.5 — `LoginView` + `LoginMVVM`

**Fields**:
- `phone: String` — masked +998 ## ### ## ##; `MaskedPhoneField` component.
- `password: String` — secure with eye toggle.

**Buttons**:
- "Sign in" → calls `POST user/login`. Disabled while loading or invalid.
- "Forgot password" → `PasswordRecoveryView`.
- "Sign up" → `SignUpView`.

**Validation (computed)**:
```swift
var canSubmit: Bool {
    phone.digitsOnly.count >= Constants.phoneNumberSize - 1 &&
    password.count >= Constants.passwordSize
}
```

**Pre-call**: ensure `CLLocationManager.locationServicesEnabled()`; if off, show alert linking to Settings.

**Response routing**:
- `driverStatus == .userInfoCompleted (5)`: show toast "Signup incomplete" — return to signup verify (no nav).
- `driverStatus == .verifyCodeConfirmed (7)`: `CompleteDriverInfoView`.
- `(.driverInfoCompleted | .driverActive | .driverTurnedNotActive | .userInfoDeleted)`: store auth token in Keychain, store user in `UserManager`, register device token (`POST device-token/register`), navigate to `MainView`. If permissions not granted yet, route via `AccessPermissionsView` first.

### 17.6 — `SignUpView` + `SignUpMVVM`

**Fields**: phone, firstName, fatherName, lastName, password, confirmPassword, agreeToTerms (Bool).

**Validation**:
- phone length ≥ `phoneNumberSize - 1`
- all name fields trimmed non-empty
- password ≥ `passwordSize`
- passwords match
- agreeToTerms == true

**Terms tap** → `TermsOfUseView` (sheet, 92 % detent): fetches `GET license/index`. Scrolling to bottom enables the "Accept" button.

**Submit** → `POST user/register` → store `authKey` → navigate `SignUpVerifyView(authKey)`.

### 17.7 — `SignUpVerifyView` + `SignUpVerifyMVVM`

**UI**:
- 4-digit `OTPCodeField` (auto-advance, auto-submit when filled).
- "Resend code in 0:60" countdown (`waitTimeVerifyCode = 60`). When expires, enables "Resend code" button.

**Actions**:
- Auto-submit on full code → `POST user/confirm` with `auth_key`, `code`, `device_token`. On success, store token + user, navigate to `CompleteDriverInfoView`.
- Resend → `POST user/refresh` → reset timer.

### 17.8 — `PasswordRecoveryView` + `PasswordRecoveryMVVM`

**Fields**: phone, newPassword, confirmPassword (with eye toggles).

**Submit** → `POST user/recover` → store `auth_key_verify` → navigate `PasswordVerifyView`.

### 17.9 — `PasswordVerifyView` + `PasswordVerifyMVVM`

- Mirrors §17.7 OTP UI.
- On submit → `POST user/change-password` with `password`, `password_repeat`, `code`, `auth_key`.
- On success → store user + token → `MainView`.

### 17.10 — `CompleteDriverInfoView` + `CompleteDriverInfoMVVM`

Multi-section scroll form. All fields required unless noted.

**Sections & fields**:

1. **Photo** — circular avatar tap → action sheet "Camera / Photo Library". Use `PHPickerViewController` (library) and `UIImagePickerController` (camera). Compress JPEG to ≤ 1 MB.
2. **Personal**
   - Date of birth — `DatePicker` `.compact`, max date = today − 18 years
   - Gender — sheet picker fetched from server (`speciality` endpoint shares the same shape; use Region API call analogue — Android code shows it's a dropdown from `/references/gender`. If endpoint shape is unclear, hardcode `male/female/other` until backend confirmation.)
   - Address — multi-line text field
3. **Passport**
   - Passport number — text field (e.g. `AA1234567`)
   - Given by — text field
   - Given date — DatePicker
4. **Driver license**
   - License number — text field
   - License category — sheet picker (B / BC / D etc.)
5. **Vehicle**
   - Region — sheet picker from `branch/index`
   - Car brand — sheet picker from `cars/index?type=brand`
   - Car model — sheet picker from `cars/index?type=model&brand_id=…` (depends on brand)
   - Car color — sheet picker from `cars/index?type=color`
   - Car number — text field (Uzbek plate format)
   - Year made — picker
6. **Documents** — multi-select photo picker; up to 10 images.

**Validation**: each section gets a green check when complete; bottom CTA "Submit" enabled when all required fields filled and photo present.

**Submit** → multipart POST `user/fill-data`. Show full-screen progress with upload-percent. On success → set `UserManager` → navigate to `MainView`.

### 17.11 — `MainView` (TabView)

- 4 tabs: Home / My Orders / Notifications / Settings.
- Badge on My Orders = number of active orders; on Notifications = unread count.
- Home tab content depends on `AppTypeManager.shared.current`:
  - `.Navigator` → `MapHomeView`
  - `.List` → `ListHomeView`
- On appear:
  - `WebSocketManager.shared.connect()` if driver is on-shift.
  - `LocationTracker.shared.requestPermission()` if not yet granted.
- On disappear / sign-out → disconnect.

### 17.12 — `MapHomeView` + `MapMVVM` (the core driver screen)

**Layout (top → bottom)**:

- **Top bar (floating cards)**:
  - Left: Balance pill `Color.brandOrange`, white text, format `1 234 567 sum` (uses thousand separator).
  - Center: Notification badge button (bell + dot).
  - Right: Settings gear button (opens `SettingsView`).
- **Map** (full-screen behind everything): `YandexMap` with user puck + nearby orders as pins. Pinch/zoom/pan free.
- **Bottom right floating buttons**: recenter, zoom in/out (optional — Yandex's built-in works fine).
- **Bottom card** (anchored to bottom safe-area):
  - Big primary toggle: "Go online" / "Go offline" with `.brandOrange` when offline (CTA to go online) and `.green` when online.
  - When online: shows "Looking for orders…" subtitle + current shift duration.
  - When an active order exists: replaces the toggle with an order card → tap opens `TripView`.

**Behaviour**:

- Toggle online → `POST driver/start` + connect WebSocket + start `LocationTracker.startWhileOnline()`.
- Toggle offline → `POST driver/end` + disconnect WebSocket + stop location updates.
- Subscribe to `WebSocketManager.shared.orderSubject`. On `.new` or `.newPrivate`, present `OrderOfferSheet` (sheet with `.large` detent, undismissable while countdown active).
- Subscribe to `WebSocketManager.shared.notificationSubject` → update notification badge count.

### 17.13 — `OrderOfferSheet` + `OrderOfferMVVM`

- Modal sheet (or fullScreenCover for `.newPrivate`).
- Shows: pickup address, drop-off, distance (driver→pickup), distance (pickup→drop-off), tariff name, fare estimate, services, payment method (card/cash icon), client bonus if any, info text.
- Countdown ring around "Accept" — `branch.acceptWaiting` seconds (default 20).
- "Accept" → `POST order/accept?id={id}` → on success, dismiss → show `TripView`.
- "Skip" → `GET order/order-skip?order_id={id}` → dismiss.
- Plays sound on appear: `audio_private_order.caf` for private, `audio_order.caf` for broadcast.
- Haptic: heavy impact on appear.

### 17.14 — `TripView` + `TripMVVM`

Active trip screen. Drives the taximeter.

**States (`OrderState`)**:

- `.accepted (2)` — driver is heading to pickup
  - Big SlideToActButton: "Slide to arrive at pickup"
  - "Cancel" button (small, top right) opens `CancelReasonSheet`
  - "Call client" / "Call dispatcher" pills
- `.changedArrived (8)` — driver arrived, waiting for client
  - Waiting timer ticking (live `waitedTimeUntilGoneMs`)
  - SlideToActButton: "Slide to start trip"
  - Free call buttons
- `.started (7)` — trip in progress
  - Live fare display (recalculated every second per §18)
  - Distance counter, current speed, waiting time tally
  - Multi-stop list if `order.locations.count > 1` — show next stop card
  - Big button: "Finish trip" (only enabled near final destination, or always — match Android: always enabled)
- `.changedGone (9)` — multi-stop intermediate; same UI as `.started`

**State transitions**:

- Arrive: `GET order-change/state?state=8&id={id}` → on success set local state.
- Start trip ("on way"): `GET order-change/state?state=9&id={id}` → set state, call `LocationTracker.shared.markWayStarted()`.
- Finish: `POST order/complete?order_id={id}` with `RequestOrderFinish` body computed by `TripFinishMVVM` → on success show `TripFinishView`.

**Background map**: small `YandexMap` strip behind the controls showing route line + driver puck. Tappable to expand full screen.

### 17.15 — `TripFinishView` + `TripFinishMVVM`

Fare summary modal after trip completes.

- Total distance (km, 2 dp)
- Trip duration (HH:mm:ss)
- Waiting time accrued
- Fare breakdown:
  - Base = `startingPrice`
  - Distance charge = sum over `tariff.distanceIntervals`
  - Waiting charge = `max(0, waitedMs/60000 - tariff.minWaitTime) * tariff.priceOfWaiting`
  - Out-of-city = `priceOfOut * kmOut`
  - Services total
  - Bonus / promo deductions
- Total in big bold orange
- Primary "Done" → dismiss → return to `MapHomeView`.

### 17.16 — `MyOrdersView` + `MyOrdersMVVM`

- Two segments: **Active** and **History**.
- Active: `GET order/my-orders` — current driver-assigned orders. Tap row → `TripView`.
- History: paginated `GET order/history?expand=myOrder&page=N`. Lazy load on scroll. Each row shows date, route summary, total fare, status pill (color-coded). Tap → `OrderHistoryDetailView`.
- Filter chip row (all / finished / cancelled).

### 17.17 — `NotificationsView` + `NotificationsMVVM`

- `GET notification/index` returns array.
- List sorted by date desc. Each row: icon, title, snippet, timestamp.
- Tap → `NotificationDetailView` with full text, image (if any).
- Subscribe to `WebSocketManager.shared.notificationSubject` to prepend new items live.

### 17.18 — `SettingsView` + `SettingsMVVM`

A simple `Form` with `Section`s. Rows below — each opens its own detail view.

| Row | Destination | Subtitle |
|---|---|---|
| Profile | `ProfileView` | driver full name, phone |
| Balance | `BalanceView` | current balance |
| History | `HistoryView` | "View past trips" |
| Earnings | `EarningsView` | "Daily / weekly / monthly" |
| Subscriptions | `SubscriptionsView` | active plan name |
| Language | `ChooseLanguageView` | current language |
| Theme | `ChooseThemeView` | Day / Night / System |
| Map | `ChooseMapView` | current external map |
| App mode | `ChooseAppTypeView` | Navigator / List |
| Videos | `VideosView` | "Tutorials" |
| Support | `SupportView` | "Call dispatcher" |
| About | inline | version `1.9.1 (47)` |
| Sign out | confirmation alert → `SignOut` action | red |

### 17.19 — `ProfileView` + `ProfileMVVM`

Read-only profile: name, phone, region/branch, car (color + model + plate), license number. No edit in v1 — drivers contact support to amend.

### 17.20 — `BalanceView` + `BalanceMVVM`

- Header: current balance (big).
- "Top up" CTA → bottom sheet: amount input, "Pay with Click" / "Pay with PayMe" buttons.
- Click: open `https://my.click.uz/services/pay?service_id={merchantServiceIdClick}&merchant_id={merchantIdClick}&amount={amount}&transaction_param={user.id}` in `SFSafariViewController`.
- PayMe: open `https://checkout.paycom.uz/{base64(m={merchantIdPayMe};a={amount*100};ac.user_id={user.id})}` in Safari view.
- Listen for `applicationWillEnterForeground` to refresh balance via `GET user/me`.

### 17.21 — `HistoryView` + `HistoryMVVM`

Identical content to `MyOrdersView` History segment, accessed via Settings. Includes date-range filter.

### 17.22 — `EarningsView` + `EarningsMVVM`

- Segmented period: Day / Week / Month / Custom.
- Custom shows `DatePicker`s for `from` / `to`.
- Calls `GET driver-earnings/summary?period=…&from=…&to=…&tz=…`.
- DGCharts bar chart of `byDay` array.
- KPI tiles: total earned, total trips, total distance, total time online.
- Breakdown by tariff (list).

### 17.23 — `SubscriptionsView` + `SubscriptionsMVVM`

- `GET subscription/list?page=1` (paginated; lazy-load on scroll).
- Each plan card: name, price, duration, description, "Buy" button → confirmation alert → `GET subscription/purchase?id={id}` → on success refresh.

### 17.24 — `VideosView` + `VideosMVVM`

- `GET video/index` returns categorized videos.
- Tap → `VideoPlayerView` using `AVPlayer` (URL from `video.url`).
- Categories from `Constants` (Android constants list):
  - `mening_balansim` (my balance)
  - `registratsiya` (signup)
  - `profil_oynasi` (profile)
  - `buyurtmalar` (orders)
  - `mening_buyurtmalarim` (my orders)
  - `chat_oynasi` (chat)
  - `operator_bilan_aloqa` (operator)
  - `zakaz_olingan_oynasi` (order accepted)
  - `parolni_tiklash` (password recovery)
  - `buyurtmalar_ichki` (orders inner)
  - `qoshimcha` (additional)

### 17.25 — `ChooseMapView` + `ChooseMapMVVM`

5 rows (Google / Yandex / Yandex Navi / 2GIS / Waze). Selecting persists to `MapTypeManager.shared.set(_:)`. Show a "Not installed" hint next to apps whose scheme returns `false` from `canOpenURL`, with a tap-to-install link to the App Store.

### 17.26 — `SupportView` + `SupportMVVM`

- Big tile: "Call dispatcher" → opens `tel:` URL with `user.branch.dispatcherNumber`.
- FAQ list (static — pull from `Instruction/index`).
- "Send error log" button → POST queued errors via `POST user/report`.

---

## 18. Taximeter / Trip Calculation

The driver's fare is computed locally in real time during the trip, then sent to the server in `RequestOrderFinish`. The server may recompute on its side; the local value is shown to the driver and used as the final number in `TripFinishView`.

### Inputs

From the current `Order`:
- `tariff.distanceIntervals` — array of `{start, end, price}` per-km tiers (km).
- `tariff.priceOfOut` — per-km charge for distance outside the city polygon.
- `tariff.priceOfWaiting` — per-minute waiting charge **outside** the trip (post-arrival, pre-start).
- `tariff.priceOfWaitingOnWay` — per-minute waiting charge **during** the trip.
- `tariff.minWaitTime` — free waiting minutes (outside trip).
- `tariff.minWaitTimeOnWay` — free waiting minutes (on-way).
- `order.startingPrice` — base fare (minimum charge).
- `order.priceInCity` — fallback per-km if intervals empty.
- `order.services` — list of selected services with `total`.
- `order.useBonus`, `order.promoCode` — deductions.

From `LocationTracker`:
- `totalDistanceKm` (split between inside/outside `branch.polygon`)
- `trackedTimeMs`, `waitedTimeMs`, `waitedTimeUntilGoneMs`

### Formula

```
distanceCharge =
    if distanceIntervals not empty:
        for each interval [start, end] with price:
            km_in_interval = clamp(totalDistanceInsideKm, start, end) - start
            charge += km_in_interval * price
    else:
        totalDistanceInsideKm * priceInCity

outOfCityCharge = totalDistanceOutsideKm * priceOfOut

waitMinutes        = waitedTimeMs / 60_000.0
wayWaitMinutes     = waitedTimeUntilGoneMs / 60_000.0
waitChargeOutside  = max(0, waitMinutes - minWaitTime) * priceOfWaiting
waitChargeOnWay    = max(0, wayWaitMinutes - minWaitTimeOnWay) * priceOfWaitingOnWay

servicesTotal = sum of order.services[*].total
bonusApplied  = useBonus ? user.bonusAvailable : 0
promoApplied  = promoCode.usage.amount ?? 0

subtotal = startingPrice + distanceCharge + outOfCityCharge
         + waitChargeOutside + waitChargeOnWay + servicesTotal

total = max(startingPrice, subtotal) - bonusApplied - promoApplied
```

### Inside / outside city detection

`branch.polygon.boundary` is an array of `[lat, lon]` pairs. Implement point-in-polygon (ray casting) in `Geometry.swift`. Tally distance accrued inside the polygon vs outside.

### Persistence

Every tick (1 Hz) update `TripMVVM`'s `@Published var liveFare: Double`. Every 5–10 s flush counters to Core Data via `CalculationStore`. On app relaunch mid-trip, `TripMVVM.init(orderId:)` rehydrates from `CalculationStore` before resuming.

---

## 19. Error Handling Conventions

### Mapping

| `ClientError` case | UI |
|---|---|
| `.noConnection` | Toast at top: `error_no_connection`. ViewModel stays in `.Neutral` if optional, `.Negative` if blocking. |
| `.unauthorized` | Force sign-out: `UserManager.shared.signOut()`, root → `LoginView`. |
| `.server(message)` | Inline error label on form, or alert if action-triggered. |
| `.decoding` | Show generic "Something went wrong"; log to OS log + crashlytics. |
| `.unknown` | Same as decoding. |

### Validation copy

Use the keys in §16. Never show raw API messages for fields like phone/password — show our pre-validated message.

### Crash & log

Configure `FirebaseCrashlytics` so `os_log(.error, …)` events with the `APP-` prefix appear as breadcrumbs. Don't include the auth token in crash payloads.

---

## 20. Implementation Order

Build in this order. Each milestone must compile, run, and pass a manual smoke test before moving on.

### M1 — Project skeleton (1 day)
- Xcode project, capabilities, Info.plist, SPM packages (Yandex, Firebase, Kingfisher, PhoneNumberKit, DGCharts).
- `Constants.swift`, all enums, all persistence managers.
- `DynamicClient.swift` with one GET (`user/me`) end-to-end.
- App icon, accent color, launch screen.

### M2 — Auth & onboarding (2 days)
- `RootView` routing, `LanguageView`, `IntroduceView`.
- `LoginView`, `SignUpView`, `SignUpVerifyView`, `PasswordRecoveryView`, `PasswordVerifyView`.
- `CompleteDriverInfoView` with full multipart upload.
- `TermsOfUseView` modal.

### M3 — Splash + version gate (½ day)
- `SplashView`, `ForceUpdateView`, `BlockedAppView`.
- `mobile/version-driver` integration.

### M4 — Map + WebSocket + offer flow (3 days)
- Yandex MapKit wrapper.
- `LocationTracker` with permission flow.
- `WebSocketManager` (full receive + ping + reconnect).
- `MapHomeView` with online toggle, balance pill, notification badge.
- `OrderOfferSheet` with countdown + accept/skip.

### M5 — Trip & taximeter (3 days)
- `TripView` with all 4 `OrderState` UIs.
- `TripMVVM` fare engine (§18).
- `CalculationStore` (Core Data).
- `TripFinishView` summary.
- `CancelReasonSheet`, `CallDispatcherSheet`.

### M6 — Tabs (2 days)
- `MyOrdersView` (active + history segments).
- `NotificationsView` + detail.
- `SettingsView` with all sub-views.

### M7 — Settings details (2 days)
- `ProfileView`, `BalanceView` (with Click/PayMe Safari), `HistoryView`, `EarningsView` (with DGCharts), `SubscriptionsView`, `VideosView`, `ChooseMapView`, `ChooseAppTypeView`, `ChooseLanguageView`, `ChooseThemeView`, `SupportView`.

### M8 — Push notifications (1 day)
- APNs registration, FCM token forwarding, categories, tap routing.

### M9 — Polish (2 days)
- All localizations populated (uz / kk / ky / ru).
- Empty states, loading skeletons, error toasts.
- Haptics on key actions.
- Background task scheduling.
- Crashlytics integration.
- Accessibility pass (Dynamic Type, VoiceOver labels on key actions).

### M10 — QA & TestFlight (1 day)
- Field-test the offer → accept → drive → finish flow with a real backend driver account.
- Manual regression of every screen.
- Submit to TestFlight.

**Total: ~17 working days for a single engineer.**

---

## 21. Final Checklist

Before declaring this app done, **every box below must be checkable**.

### Code style compliance
- [ ] All ViewModels are `@MainActor` `ObservableObject`.
- [ ] Every ViewModel has `@Published var state: VMState = .Neutral` and `logTag = "APP-…"`.
- [ ] Every collection uses `Array<T>`, never `[T]`.
- [ ] Every `@State` / property has an explicit type annotation.
- [ ] Every file has the header block `// FileName.swift / MehrgoDriver / Created by Umar on DD/MM/YY.`.
- [ ] Imports ordered: `os.log`, `Combine`, `Foundation`, `SwiftUI`.
- [ ] `// MARK: -` sections present and in order per code style §10.

### Design system compliance (§17.0)
- [ ] `DesignSystem.swift` exists; every screen consumes `Color.*`, `Font.*`, `Space.*`, `Radius.*` tokens — **no raw hex, pt, or `.system(size:)` calls in `View` files**.
- [ ] Asset Catalog has Any/Dark variants for every color token in §17.0.2.
- [ ] All 12 core components in §17.0.8 exist under `Utilities/` and are used wherever the spec references them.
- [ ] Every screen has been rendered in **both** light and dark mode and side-by-side with Dynamic Type at Default and AX3 (Accessibility Inspector → Increase Type Size).
- [ ] Every interactive element has a VoiceOver label; icon-only buttons specifically verified.
- [ ] All `withAnimation` calls gated on `accessibilityReduceMotion`.
- [ ] WCAG AA contrast passes on brand-orange + white, body text on `bg.app`, and `text.secondary` on `bg.surface`.
- [ ] Haptics match the §17.0.7 table — no extras, no missing.
- [ ] Hit-target audit (Accessibility Inspector → "Audit") returns zero hits < 44 × 44 pt.
- [ ] All sheets use `.presentationDetents`; full-screen cover used only for private offer, force update, and signup multi-step.

### Functional parity with Android v1.9.1
- [ ] All 47+ REST endpoints in §10 wired to a ViewModel.
- [ ] WebSocket connects, pings every 10 s, handles all 7 message keys, auto-reconnects.
- [ ] Driver can complete a full flow: signup → SMS verify → driver info upload → online → receive offer → accept → drive → finish → see fare.
- [ ] Multi-stop orders show next-destination cards.
- [ ] Taximeter math matches Android: starting price + intervals + out-of-city + waiting (split) + services − bonus − promo, floor at `startingPrice`.
- [ ] Order cancel with reason works.
- [ ] All 4 languages render correctly (test by switching mid-session).
- [ ] Theme switching (light / dark / system) honored everywhere.
- [ ] Balance top-up opens Click and PayMe in Safari view.
- [ ] External map deep-link works for all 5 apps, with Apple Maps fallback.

### iOS-specific quality
- [ ] App requests Always location once, with clear pre-prompt explaining why.
- [ ] Background-location runs while online; CPU/battery profile is reasonable (test 1 h shift).
- [ ] APNs token forwarded to backend; offer push (with critical alert sound) arrives when app is killed.
- [ ] Crashlytics records crashes; `os_log` errors appear as breadcrumbs.
- [ ] Dynamic Type works at default + accessibility-large sizes.
- [ ] VoiceOver: every CTA has a label; `OrderOfferSheet` announces "New private order, 20 seconds to accept".
- [ ] Portrait-only orientation enforced.
- [ ] App passes `xcodebuild test` (unit tests for taximeter math + decoder smoke tests).

### Release
- [ ] App Store screenshots in all 4 languages.
- [ ] Privacy nutrition label declared: Location (App Functionality), Phone Number (Account), Contact Info (Account), Identifiers (Analytics + App Functionality), Diagnostics (App Functionality).
- [ ] App Store age rating: 4+.
- [ ] App Store category: Navigation (primary), Business (secondary).
- [ ] Support URL + Privacy Policy URL filled.

---

## Appendix A — Repository / Use-Case Mirror (for organization reference)

The Android codebase uses Clean-Architecture use cases (`*UC`). On iOS, **we collapse them into the ViewModel** that owns them — there's no separate use-case layer. The table below is a one-line cross-reference so a developer transitioning between the two codebases can find their way.

| Android UC | iOS owner |
|---|---|
| `LoginUC` | `LoginMVVM.signIn()` |
| `SignUpUC` | `SignUpMVVM.submit()` |
| `SignUpVerifyCodeUC`, `SignUpResendCodeUC` | `SignUpVerifyMVVM.verify()`, `.resend()` |
| `PasswordRecoveryUC`, `PasswordRecoveryResendCodeUC`, `PasswordRecoveryChangePasswordUC` | `PasswordRecoveryMVVM` / `PasswordVerifyMVVM` |
| `RegionsUC`, `CarBrandsUC`, `CarModelsUC`, `CarColorsUC` | `CompleteDriverInfoMVVM.loadReferenceData()` |
| `UploadDriverInfoUC` | `CompleteDriverInfoMVVM.submit()` |
| `IntroduceUC`, `TermsOfUseUC` | `IntroduceMVVM.load()`, `SignUpMVVM.loadTerms()` |
| `RegisterDeviceTokenUC` | `PushNotificationCenter.syncToken()` |
| `UserUC` | `RootMVVM.fetchUser()` / `ProfileMVVM.load()` |
| `OrdersUC`, `AllOrdersUC`, `OrderAddressUC` | `ListHomeMVVM` (only used in List mode) |
| `ActiveMyOrdersUC` | `MyOrdersMVVM.loadActive()` |
| `OrderAcceptUC`, `OrderSkipUC` | `OrderOfferMVVM.accept()`, `.skip()` |
| `OrderStartUC`, `OrderArriveUC`, `OrderGoUC`, `OrderFinishUC` | `TripMVVM.start()`, `.arrive()`, `.go()`, `.finish()` |
| `OrderCancelUC`, `OrderCancelReasonsUC` | `CancelReasonSheet` / `TripMVVM.cancel()` |
| `OrderServicesUC`, `AddRemoveServiceUC` | `TripMVVM.loadServices()`, `.toggleService()` |
| `OrderCreateUC` | (not used in v1 — driver-created taximeter orders. Stub in `ListHomeMVVM` only if List mode launches.) |
| `AddressInBranchUC`, `GetTariffsUC` | `ListHomeMVVM.loadTariffs()` |
| `SendLocationUC`, `UploadLocationUC` | `LocationTracker.uploadBatchToServer()` |
| `DriverEarningsSummaryUC` | `EarningsMVVM.load()` |
| `NotificationsUC` | `NotificationsMVVM.load()` |
| `InstructionUC`, `VideosUC` | `SupportMVVM.load()`, `VideosMVVM.load()` |
| `RecommendOrderUC` | `MapMVVM.loadRecommended()` |
| `PurchaseSubscriptionUC` | `SubscriptionsMVVM.purchase(id:)` |
| `StartWorkUC`, `FinishWorkUC` | `MapMVVM.toggleOnline()` |
| `ChangeLanguageUC` | `LanguageManager.set(_:)` (also POSTs to server) |
| `UpdateAppUC` | `SplashMVVM.checkVersion()` |
| `RouteUC`, `GetDistanceUC` | `RouteService.route(...)` in `Shared/` |
| Local Room `*UC` | `CalculationStore` methods |

---

## Appendix B — Open questions / known unknowns

These are items where the Android source either has placeholder behaviour or where iOS needs an explicit design call. Fill them in as you build:

1. **App Store ID** — needed for `ForceUpdateView` link. Insert after first App Store Connect submission.
2. **`speciality_id` and `gender` enum values for `user/fill-data`** — Android fetches them from a `/references/...` endpoint not enumerated in this spec. Confirm the exact endpoint with the backend team during M2; until then hardcode plausible values and gate with a feature flag.
3. **Click / PayMe redirect URL format** — verify exact transaction-param naming with the payments team. The format in §17.20 is the most common pattern but the Android code uses an embedded SDK on Click side; iOS will use a WebView redirect instead.
4. **`audio_private_order.caf`** — convert the Android `audio_private_order.mp3` to CAF for use as a critical-alert sound. Apple requires sounds ≤ 30 s, encoded as PCM/AAC in CAF/AIFF/WAV.
5. **Critical alert entitlement** — request from Apple if you want the private-offer push to bypass Do-Not-Disturb. Otherwise time-sensitive interruption level is the best you get.
6. **Phone number formats outside Uzbekistan** — Mehrgo is UZ-only, so the 13-digit `+998XXXXXXXXX` is the only mask. If the brand ever expands to KG/KZ later, revisit `MaskedPhoneField`.

---

## Appendix C — Screen → Component Quick Reference

A scannable index showing which §17.0 components each screen depends on. Use this to ensure no screen invents a one-off control.

| Screen | Layout primitive | Components from §17.0.8 | Specific touches |
|---|---|---|---|
| `RootView` (§17.1) | `NavigationStack` host | — | Reads `RootRoute`, swaps subtree. |
| `SplashView` (§17.2) | Centered logo on `bg.app` | `LoadingShimmer`-free; just a pulsing logo | 1.5 s minimum dwell, brand-orange pulse. |
| `ForceUpdateView` (§17.2) | Full-screen cover | `PrimaryButton` ("Update"), `TextButton` ("Later") | Hide "Later" if `required == true`. |
| `BlockedAppView` (§17.2) | Full-screen cover | `PrimaryButton`, `Pill (.danger)` | Informational; iOS cannot enforce. |
| `LanguageView` (§17.3) | Vertical list of 4 rows | `Card`, selected row with `brand.primarySoft` background | Auto-advance to `IntroduceView` on tap. |
| `IntroduceView` (§17.4) | `TabView(.page)` | `PrimaryButton`, `TextButton` ("Skip") | iOS-specific slide near end (see §17.4). |
| `LoginView` (§17.5) | `Form`-style stack | `MaskedPhoneField`, `SecureField`, `PrimaryButton`, `TextButton` ×2 | Pre-permission location check. |
| `SignUpView` (§17.6) | Scrollable form | `MaskedPhoneField`, text fields, `Toggle` (terms), `PrimaryButton` | Terms sheet `.medium` detent → `.large` on scroll. |
| `SignUpVerifyView` (§17.7) | Centered column | `OTPCodeField`, `CountdownRing` (resend), `SecondaryButton` | Auto-submit on full code. |
| `PasswordRecoveryView` (§17.8) | Form | `MaskedPhoneField`, two `SecureField`s, `PrimaryButton` | Mirror of LoginView. |
| `PasswordVerifyView` (§17.9) | Centered column | `OTPCodeField`, `CountdownRing` | Identical to §17.7 OTP. |
| `CompleteDriverInfoView` (§17.10) | Sectioned scroll form | `Card` (one per section), photo picker, sheet pickers, `PrimaryButton` | Each section gets a green check when complete. |
| `MainView` (§17.11) | `TabView` | `NotificationBadge` on bell tab | Hide tab bar on `TripView` via `.toolbar(.hidden, for: .tabBar)`. |
| `MapHomeView` (§17.12) | `ZStack` map + floating overlays | `BalanceBadge`, `NotificationBadge`, `IconBadge` (gear), `OnlineToggle` | Top overlay = `.overlay(alignment: .top)`. Bottom toggle = `.safeAreaInset(.bottom)`. |
| `OrderOfferSheet` (§17.13) | `.sheet` (broadcast) / `.fullScreenCover` (private) | `Card`, `Pill` (services, payment), `CountdownRing`, `PrimaryButton` ("Accept"), `SecondaryButton` ("Skip") | Private offer plays critical sound + heavy haptic ×3. |
| `TripView` (§17.14) | Bottom panel over map strip | `SlideToActButton`, `Pill` (state), `IconBadge` (call), live mono numerals | One slide-to-act per `OrderState`. |
| `TripFinishView` (§17.15) | `.sheet(.large)` | `Card` (breakdown rows), `displayLarge` total, `PrimaryButton` ("Done") | Total in `brand.primary`. |
| `MyOrdersView` (§17.16) | Segmented control + `List` | `Pill` (status), `Card` row, `LoadingShimmer`, `EmptyStateView` | Lazy paginate history. |
| `NotificationsView` (§17.17) | `List` | `IconBadge`, `Pill (.brand)` for unread | Subscribe to socket subject to prepend live. |
| `SettingsView` (§17.18) | `Form` with `Section`s | `IconBadge` per row, trailing chevron, version footer | Sign-out row in `semantic.danger`. |
| `ProfileView` (§17.19) | Header + read-only `Card`s | Avatar, `Pill` (status), `IconBadge` rows | No edit in v1. |
| `BalanceView` (§17.20) | Hero + actions | `displayLarge` balance, `PrimaryButton` ("Top up"), `.sheet(.medium)` amount sheet | Click + PayMe open in `SFSafariViewController`. |
| `HistoryView` (§17.21) | Same as MyOrders → History | `Pill`, `Card` | Date-range filter at top. |
| `EarningsView` (§17.22) | Segmented + chart | DGCharts bar chart, KPI tiles (`Card`), tariff list rows | KPI tiles 2-up grid. |
| `SubscriptionsView` (§17.23) | List of plans | `Card`, `PrimaryButton` ("Buy"), confirmation `.alert` | — |
| `VideosView` (§17.24) | Categorized list | `IconBadge`, `Card`, thumbnail image | Tap → `AVPlayer` in full screen. |
| `ChooseMapView` (§17.25) | List of 5 rows | `Card`, "Not installed" `Pill (.warning)` | App Store link when scheme missing. |
| `SupportView` (§17.26) | List | `PrimaryButton` ("Call dispatcher"), instruction `Card`s | `tel:` URL → dispatcher number. |

---

*Spec version: 1.1 — updated 2026-05-19 against Android branch `mehrgo_driver_app`, commit `3e7291a6`. §17.0 expanded into a full design system. When the backend API contract changes, update §10 and bump this version.*
