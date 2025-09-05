# CLAUDE.md

- Premature Optimization is the Root of All Evil
- 一切忖度しないこと
- 回答には日本語を利用すること
- 全角と半角の間には半角スペースを入れること

## レビューについて

- レビューはかなり厳しくすること
- レビューの表現は、シンプルにすること
- レビューの表現は、日本語で行うこと
- レビューの表現は、指摘内容を明確にすること
- レビューの表現は、指摘内容を具体的にすること
- レビューの表現は、指摘内容を優先順位をつけること
- レビューの表現は、指摘内容を優先順位をつけて、重要なものから順に記載すること

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ConnectedTank is a Fabric mod for Minecraft 1.21.8 written in Kotlin. The project uses Fabric Loom for build management and includes both client and server-side components.

## Build System

This project uses Gradle with Kotlin DSL. Key commands:

- `./gradlew build` - Build the mod
- `./gradlew runClient` - Run Minecraft client with the mod for testing
- `./gradlew runServer` - Run Minecraft server with the mod for testing
- `./gradlew runDatagen` - Generate mod data (recipes, loot tables, etc.)
- `./gradlew spotlessApply` - Format code (Kotlin with ktlint, Java with Palantir Java Format)
- `./gradlew spotlessCheck` - Check code formatting

## Architecture

The mod follows Fabric's standard structure:

- **Main mod class**: `ConnectedTank.kt` - ModInitializer that registers blocks and items
- **Client-side code**: Located in `src/client/kotlin/` - handles client-specific functionality
- **Blocks**: `CTBlocks.kt` - block registration and definitions
- **Items**: `CTItems.kt` - item registration and definitions
- **Data generation**: `ConnectedTankDataGenerator.kt` - generates recipes, models, etc.
- **Mixins**: Located in `src/main/java/` and `src/client/java/` for Java compatibility

## Development Environment

- **Java Version**: 21
- **Kotlin Version**: 2.2.10
- **Minecraft Version**: 1.21.8
- **Fabric Loader**: 0.17.2
- **Split source sets**: Client and server code are separated using Loom's splitEnvironmentSourceSets()

## Code Style

The project uses Spotless for code formatting:
- Kotlin files: ktlint
- Java files: Palantir Java Format
- Kotlin Gradle files: ktlint

Run formatting before committing changes.

## Required Task Completion Checks

After any code edits, ALWAYS run the following commands to ensure code quality:

1. `./gradlew spotlessApply` - Format code automatically
2. `./gradlew spotlessCheck` - Verify code formatting
3. `./gradlew build` - Build and run lint checks

These steps are mandatory after every code modification.

**Note**: If required checks cannot be executed locally, GitHub Actions results can be used as an alternative. Verify code quality and build success through CI/CD execution results.

## Development Documentation
Useful development manuals are available in the `docs/` directory:
- **`MinecraftSourceExploration.md`** - Minecraft source code analysis techniques
    - Methods for exploring jar files in `.gradle/loom-cache`
