# The Eye of Sauron True Random Number Generator

Have you ever looked at an old Android phone and thought, "This could be more... useful?" Do you crave random numbers so genuinely unpredictable they could be used for cryptography or scientific modeling? Look no further.

This project transforms a standard Android phone into **The Eye of Sauron**, a ludicrously powerful, cryptographically secure, True Random Number Generator (TRNG) appliance.

![Screenshot of the main app screen](https://raw.githubusercontent.com/cperos-xr/LaserSpeckleRNG/main/main/images/screen.jpg)

## What is This? (A True Random Number Generator)

Standard random functions (`Math.random()`) are just clever illusions—**Pseudo-Random Number Generators (PRNGs)**. They use predictable math that will eventually repeat, making them fine for video game sparkle effects, but unsuitable for anything requiring true unpredictability.

This appliance is a **True Random Number Generator (TRNG)**.

- **Entropy Source:** It derives its randomness from the chaotic, quantum-level patterns of **laser speckle**. It is literally watching the universe argue with itself and writing down the results.
- **Predictability:** The output is fundamentally unpredictable. Even with complete knowledge of all previous numbers, the next one is a total mystery.
- **Use Cases:** Essential for cryptography, scientific modeling, high-stakes lotteries, and, of course, ensuring your D&D night is governed by truly chaotic forces.

## The Hardware: Forging the Great Eye

To complete the project, you'll need the custom 3D-printed housing, which gives the all-seeing Eye a fitting and functional home.

**[>> Download the 3D Model from Thingiverse <<](https://www.thingiverse.com/thing:7304290)**

The housing holds the phone and a simple laser module in perfect alignment. The app even includes an optional "Tower" overlay to complete the all-seeing-eye aesthetic.

![The laser setup](https://raw.githubusercontent.com/cperos-xr/LaserSpeckleRNG/main/main/images/laser.jpg)

## Key Features

- **True Hardware Entropy:** Randomness forged in the fires of quantum physics.
- **On-Screen D&D Dice Roller:** For when you need to smite a goblin with numbers blessed by chaos itself.
- **3D-Printable Housing:** Because every good project deserves a cool case.
- **Always-On Foreground Service:** The Great Eye is ever watchful.
- **Embedded Web Server:** Access the Eye's chaotic whispers from any device on your network via the API.
- **Comprehensive Statistical Analysis:** A PIN-protected page to verify that the randomness is, indeed, of the highest quality.
- **Glitch Detection:** Automatically discards flawed data if the camera sensor has a momentary lapse.

## The API

The embedded web server provides a simple HTML interface and a powerful REST API.

![Screenshot of the web analysis page](https://raw.githubusercontent.com/cperos-xr/LaserSpeckleRNG/main/main/images/API.png)

### API Endpoints

- **`/`**: A live view into the abyss.
- **`/status`**: Get a raw JSON status report.
- **`/rng?min=X&max=Y`**: Generate a number in a custom range.
- **`/help`**: A list of available commands.
- **/d4, /d6, /d8, /d10, /d12, /d20, /d100**: Roll standard D&D dice.
- **/coin**: Flip a coin.

### Admin Endpoints (PIN Required)
- **`/log?pin=PIN`**: View the raw log of all generated numbers.
- **`/analysis?pin=PIN`**: View the Chi-Squared test and other stats.

**Note on the PIN:** The admin endpoints are protected by a PIN (`123456`), which is displayed on the device's screen. This is to prevent you from *accidentally* deleting your precious data, not to stop a determined thief.

## How to Summon the Eye

1.  **Print the Housing:** Download the model from Thingiverse.
2.  **Assemble the Hardware:** Install a laser module and your phone.
3.  **Install the App:** Go to the [**official GitHub repository**](https://github.com/cperos-xr/LaserSpeckleRNG) and download the latest pre-compiled `.apk` file from the **Releases** section on the right-hand side.
4.  **Connect to Wi-Fi** on the same network as your computer.
5.  **Focus the Laser:** Use the on-screen slider until the speckle pattern is sharp.
6.  **Roll the Dice!** Use the on-screen buttons or the API.

## Troubleshooting

Can't connect to the API from another device? Network settings can sometimes be tricky.

- **Check for AP/Client Isolation:** Many routers prevent devices on the same Wi-Fi network from talking to each other. Log into your router's settings and disable any feature called "AP Isolation," "Client Isolation," or "Guest Mode."
- **The Ol' On-and-Off:** Sometimes, simply toggling your phone's Wi-Fi or Airplane Mode is enough to appease the network spirits.
