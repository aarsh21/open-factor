# Open Factor

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

An independently maintained GPLv3 project based on NuvioMedia/NuvioMobile, with base-app credits and original license notices retained. The Android workout application uses Kotlin and Jetpack Compose, matching the base project's technology. The upstream media application is retained as reference; Open Factor builds separately to avoid requiring streaming services, media engines, or their credentials.

## Product Purpose

Build an open source workout application called Open Factor. Preserve the features, exercises, interactions and screen layouts observed in the supplied MacroFactor Workouts APK, with Nuvio's floating pill navigation and settings presentation as the user's explicit exceptions.

## Users

People recording and reviewing workouts on Android. Further audience details are undecided.

## Capabilities and Constraints

The requested scope includes exercise discovery and details, equipment, workout/program creation, set logging, workout history, progress, settings, and working exercise media. Feature completeness must be measured against APK and runtime evidence rather than assumed from names. GitHub progress commits are explicitly authorized.

The code is GPLv3, inherited from Nuvio. Asset provenance and redistribution rights must be recorded independently of the code license. Local reference imports must not silently be represented as GPL assets.

## Brand Commitments

Name: Open Factor. Preserve the workout app's observed UI and UX, except use the Nuvio navigation pill and Nuvio-style grouped settings. The supplied screenshots are the visual authority for those two surfaces.

## Evidence on Hand

- `/home/buhh/Downloads/Workouts.apk` (MacroFactor Workouts, installed package `com.sbs.train`, version 1.4.1).
- Connected Android phone; all four APK splits copied locally through REA.
- Nuvio upstream commit `c78756d0cbda3337cfff36a95d8b6708f48c0d72`.
- Two user screenshots of Nuvio navigation/settings.
- Private inspection evidence outside the public repository. Feature and asset inventories belong in `docs/open-factor/`.

## Product Principles

1. Match observed behavior and record unresolved gaps honestly.
2. Keep exercise and workout data useful offline.
3. Preserve user data across navigation and app restarts.
4. Keep a reproducible public build and attributable asset pipeline.

## Open Decisions

- Account/cloud synchronization requirements have not been specified; local persistence is the implementation starting point.
- Exact exercise metadata and remote video mapping are under investigation.
