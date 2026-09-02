# CloudStreamExtGen

An Android app that automatically generates Cloudstream extensions from website URLs. Built with Jetpack Compose and Material 3.

## Features

- **Website Analysis** - Automatically analyzes HTML, JSON, and JavaScript structures
- **Search Detection** - Discovers search functionality on websites
- **Content Analysis** - Identifies content listing patterns
- **Video Extraction** - Detects video sources and embed logic
- **Episode Detection** - Identifies season/episode structures
- **Anti-Bot Bypass** - Handles Cloudflare, CAPTCHA, and other protections
- **Code Generation** - Creates Cloudstream-compatible Kotlin extensions
- **GitHub Publishing** - Push extensions directly to GitHub repos
- **Automated Building** - Uses GitHub Actions to build .cs3 files

## How It Works

1. Enter a website URL
2. The app analyzes the site structure (HTML/JSON/JS)
3. It detects search, content, detail, episode, and video patterns
4. Cloudstream-compatible Kotlin code is generated
5. Preview the code and publish to GitHub
6. GitHub Actions builds the extension automatically

## Architecture

```
app/
├── analysis/           # Website analysis engine
│   ├── WebsiteAnalyzer.kt
│   ├── HtmlAnalyzer.kt
│   ├── JsonAnalyzer.kt
│   ├── JsAnalyzer.kt
│   └── detectors/      # Feature detectors
├── generator/          # Code generation
│   └── ExtensionGenerator.kt
├── github/             # GitHub integration
│   └── GitHubIntegration.kt
├── models/             # Data models
│   └── Models.kt
└── ui/                 # Jetpack Compose UI
    ├── theme/
    └── screens/
```

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- OkHttp
- Jsoup
- Kotlinx Serialization
- GitHub REST API

## Building

Debug APK is automatically built via GitHub Actions on every push.
Download from the [Actions](../../actions) tab.

## License

MIT License
