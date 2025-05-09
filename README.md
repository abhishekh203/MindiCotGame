# MindiKot - Android Card Game

MindiKot is a classic Indian trick-taking card game implemented as an Android application using Kotlin and Jetpack Compose. This version focuses on a four-player setup with one human player and three AI opponents, implementing the "Band Hukum" (closed trump) Version B rules.

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [Game Rules Implemented](#game-rules-implemented)
- [How to Play (In-App)](#how-to-play-in-app)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Setup and Installation](#setup-and-installation)
- [Future Enhancements](#future-enhancements)
- [Acknowledgements](#acknowledgements)

## Introduction

MindiKot (also known as Mendikot, Mendhi Coat, or Dehla Pakad in some variations) is a popular partnership card game played in India, particularly in Maharashtra and Gujarat. The primary objective is for a team to capture tricks containing Tens. This project brings the game to Android, allowing users to play against AI opponents.

This implementation specifically focuses on:
* **Four Players:** One human player and three AI-controlled players.
* **Partnerships:** The human player (Player 1) is partnered with an AI player (Player 3), against a team of two other AI players (Player 2 and Player 4).
* **Trump Selection:** "Band Hukum" (closed trump) method.
* **Band Hukum Variant:** Version B, where the trump can be optionally revealed by a player who cannot follow suit.

## Features

* **Single Human Player vs. AI:** Play as one player teamed up with an AI against two other AI opponents.
* **AI Opponents:** AI players with configurable difficulty levels (Easy, Medium, Hard) capable of:
    * Strategic hidden trump selection.
    * Rule-compliant card play (following suit, playing trumps).
    * Requesting trump reveal (Band Hukum Version B).
    * Leading, following, and discarding cards based on game state.
* **Band Hukum (Version B) Implementation:**
    * Player to the dealer's right selects a hidden trump card.
    * Players unable to follow suit have the *option* to ask for the trump to be revealed.
    * If a player asks, they *must* play a trump if they have one.
* **Complete Game Flow:**
    * Dealing cards in specified batches.
    * Turn-based gameplay.
    * Trick resolution and scoring (counting Tens and tricks).
    * Determining the winner of each deal.
    * Logic for determining the next dealer based on game rules (including whitewash scenarios).
* **Interactive UI:**
    * Built with Jetpack Compose for a modern, reactive user interface.
    * Clear display of player hands, current trick, scores, and game messages.
    * User-friendly card selection and action buttons.
* **Game Setup Options:**
    * Select Band Hukum variant (though Version B is the primary focus of this project's logic).
    * Choose AI difficulty level.

## Game Rules Implemented

* **Players & Deck:** 4 players, standard 52-card deck. A-K-Q-J-10-9-8-7-6-5-4-3-2.
* **Partnerships:** Players sitting opposite are partners.
* **Dealing:** Anticlockwise, 13 cards each (batches of 5, then 4, then 4).
* **Trump Selection (Band Hukum - Version B):**
    1.  Player to dealer's right selects a card from their hand face down; its suit is the hidden trump.
    2.  If a player cannot follow suit, they *may* ask for the trump to be revealed before playing.
    3.  If asked, the hidden card is shown, its suit becomes trump, and the card returns to the selector's hand.
    4.  The player who asked *must* play a trump if they have one.
    5.  Cards played before trump reveal (even in the same trick) do not count as trumps.
* **Play:** Anticlockwise. Must follow suit. If void, can play any card (or ask to reveal trump as per Version B). Highest card of suit led wins, unless trumps are played (highest trump wins). Winner of trick leads next.
* **Scoring a Deal:**
    * Side with 3 or 4 Tens wins.
    * If 2 Tens each, side with 7+ tricks wins.
    * Capturing all 4 Tens is a "Mendikot".
    * Capturing all 13 tricks is a "Whitewash" or "52-card Mendikot".
* **Next Dealer:**
    * Dealer's team loses: Same dealer (unless whitewashed, then dealer's partner deals).
    * Dealer's team wins: Turn to deal passes to the right.

## How to Play (In-App)

1.  **Setup:**
    * On the initial screen, confirm "Version B" for Band Hukum rules.
    * Select the desired AI difficulty.
    * Tap "Start New Deal".
2.  **Trump Selection:**
    * If it's your turn (or your AI partner's) to select the hidden trump, the game will prompt you.
    * If it's your turn, tap on a card from your hand to designate it as the hidden trump.
3.  **Playing Cards:**
    * The player to the dealer's right leads the first trick.
    * When it's your turn:
        * If you are leading, tap any card from your hand to play.
        * If following, you must play a card of the suit that was led if you have one. Playable cards will typically be highlighted or interactive.
        * If you cannot follow suit (and trump is not yet revealed for Version B):
            * You will see an option "Ask to Reveal Trump".
            * Alternatively, you can play any card from your hand (discard).
        * If you asked to reveal trump (or it was revealed automatically in Version A):
            * You *must* play a trump card if you have one.
            * If you don't have a trump, you can play any card.
4.  **Winning:**
    * The game automatically tracks tricks and Tens.
    * The deal ends after 13 tricks, and the winner is announced.
    * Follow prompts to start the next deal.

## Tech Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose (Android's modern toolkit for building native UI)
* **Architecture:** MVVM (Model-View-ViewModel) with `GameViewModel` managing state and logic.
* **Build Tool:** Gradle
* **IDE:** Android Studio

## Project Structure

The project is organized into the following main packages:

* `com.example.mindicotgame`
    * `MainActivity.kt`: Entry point of the application, sets up the UI.
    * `GameViewModel.kt`: Contains the core game logic, state management, and interactions between the UI and game models.
    * `model/`: Contains data classes and enums representing game entities:
        * `Card.kt`, `Deck.kt`, `Player.kt`, `Team.kt`
        * `Suit.kt`, `Rank.kt`, `BandHukumVariant.kt`
    * `logic/`: Contains AI-specific logic:
        * `AiPlayerLogic.kt`: Defines AI decision-making processes and difficulty levels.
        * `AiGameState.kt`, `AiDecision.kt`
    * `ui.theme/`: Standard Jetpack Compose theme files.

## Setup and Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/mindicot-game.git
    ```
2.  **Open in Android Studio:**
    * Open Android Studio (latest stable version recommended).
    * Select "Open an Existing Project".
    * Navigate to the cloned `mindicot-game` directory and open it.
3.  **Build the project:**
    * Android Studio should automatically sync Gradle files. If not, trigger a manual sync (File > Sync Project with Gradle Files).
    * Build the project (Build > Make Project).
4.  **Run the application:**
    * Select an Android Virtual Device (AVD) or connect a physical Android device.
    * Click the "Run" button (or Shift+F10).

## Future Enhancements

* **Advanced AI:** Implement even more sophisticated AI strategies for higher difficulty levels.
* **More Trump Selection Methods:** Add other common trump selection methods (e.g., random draw, cut hukum).
* **Multiplayer:** Introduce online or local network multiplayer functionality.
* **Scoring Variations:** Allow for different scoring rules or cumulative scoring across multiple deals.
* **Customization:** More UI themes, card designs.
* **Sound Effects & Animations:** Enhance user experience with sounds and more elaborate animations.
* **Detailed Statistics:** Track player statistics over time.

## Acknowledgements

* The rules for Mendikot were referenced from various online sources, including pagat.com.
* This project was developed as a learning exercise and a demonstration of game development on Android using Kotlin and Jetpack Compose
