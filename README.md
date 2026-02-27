# SpeckleRng - The Eye of Sauron TRNG Appliance

SpeckleRng transforms a standard Android phone and a 3D-printed housing into a dedicated hardware appliance for generating **true random numbers**. Unlike pseudo-random number generators (PRNGs) found in most software, SpeckleRng uses a physical, unpredictable process to generate entropy, making its output fundamentally unpredictable and suitable for security-sensitive applications.

This project is designed to be paired with a 3D-printed housing, which turns the entire appliance into a fun tribute to the **Eye of Sauron** from *The Lord of the Rings*.

![Screenshot of the main app screen](https://raw.githubusercontent.com/cperos-xr/LaserSpeckleRNG/main/main/images/screen.jpg)

## True Randomness vs. Pseudo-Randomness

Standard random number functions (`Math.random()`, `random.range()`) are **Pseudo-Random Number Generators (PRNGs)**. They use a deterministic algorithm that is predictable and repeatable, making them unsuitable for security applications.

SpeckleRng is a **True Random Number Generator (TRNG)**.

- **Entropy Source:** It derives its randomness from the chaotic, quantum-level patterns of **laser speckle** captured by the phone's camera sensor.
- **Predictability:** The output is fundamentally unpredictable. Even with complete knowledge of all previous numbers, you cannot predict the next one.
- **Use Cases:** Essential for cryptography, scientific modeling, high-stakes lotteries, Dungeons & Dragons, and any application where genuine unpredictability is paramount.

## The Hardware: The Eye of Sauron

To create the ultimate random number generator, you need the ultimate housing. This project is designed to be used with a custom 3D-printed model available on Thingiverse.

The housing holds the Android phone and a simple laser module in perfect alignment, ensuring the camera is always focused on the laser speckle pattern. The app includes an optional "Tower" overlay that completes the look, turning your entropy generator into the all-seeing Eye of Sauron.

![The laser setup](https://raw.githubusercontent.com/cperos-xr/LaserSpeckleRNG/main/main/images/laser.jpg)

## Features

- **True Hardware Entropy:** Generates random numbers from the quantum-level unpredictability of laser speckle.
- **On-Screen D&D Dice Roller:** Buttons on the main screen allow for quick, satisfying rolls of d4, d6, d8, d10, d12, d20, and d100 dice, with a large, upside-down display for easy reading.
- **3D-Printable Housing:** A custom-designed housing on Thingiverse makes assembly easy and fun.
- **"Eye of Sauron" Mode:** An optional tower overlay for purely aesthetic reasons.
- **Always-On Foreground Service:** Runs 24/7, even when the app is in the background.
- **Embedded Web Server:** A Ktor server on port 8080 exposes a RESTful API to access the random numbers from any device on your network.
- **Live Web Interface:** A simple web UI provides a real-time view of the entropy source and system status.
- **Comprehensive Statistical Analysis:** A PIN-protected webpage provides a Chi-Squared test and distribution charts. **Note:** This analysis is calibrated specifically for results generated in a 1-100 range.
- **Glitch Detection:** Automatically detects and isolates sensor glitches to protect the integrity of the random data.
- **Manual Focus Control:** A slider on the main screen provides absolute focus control to maximize entropy.
- **Auto Power Save:** An optional mode dims the screen when the battery is low to ensure 24/7 operation.

## Web API and Interface

The embedded web server provides a simple HTML interface and a powerful REST API for generating numbers and checking the system's status.

![Screenshot of the web analysis page](https://raw.githubusercontent.com/cperos-xr/LaserSpeckleRNG/main/main/images/API.png)

For more advanced analysis, the raw log data can be pulled and processed externally. The included `test_rng.bat` script, for example, can be used to generate thousands of numbers and perform a Chi-Squared test to verify the quality of the randomness.

![Screenshot of the external analysis script](https://raw.githubusercontent.com/cperos-xr/LaserSpeckleRNG/main/main/images/stats.png)

### API Endpoints

The API is divided into two categories: public endpoints for generating numbers and administrative endpoints for managing the device.

#### Public Endpoints
- **`/`**: The main HTML interface, showing a live view and status.
- **`/status`**: Get a raw JSON object of the current status.
- **`/rng?min=X&max=Y`**: Generate a new random number within the specified range.
- **`/help`**: Get a plain text list of all available API commands.
- **/d4, /d6, /d8, /d10, /d12, /d20, /d100**: Get a single roll for the specified D&D die.
- **/coin**: Flip a coin (returns a value of 1 or 2).

#### Admin Endpoints
- **`/log?pin=PIN`**: View the complete, raw log of all generated receipts.
- **`/analysis?pin=PIN`**: View the statistical analysis and randomness report.
- **`/clear_log?pin=PIN`**: Delete all logs and duplicate frame records.
- **`/duplicates?pin=PIN`**: View links to any saved glitched frames.

**Note on the PIN:** The administrative endpoints are protected by a hardcoded PIN (`123456`) to prevent casual tampering. This PIN is displayed on the device's screen for your convenience. This is not a high-security measure but is intended to prevent accidental data deletion.

### API Examples (using `curl`)

Replace `YOUR_DEVICE_IP` with the IP address shown on the app's screen.

**Get the current status:**
```bash
curl http://YOUR_DEVICE_IP:8080/status
```

**Roll a d20:**
```bash
curl http://YOUR_DEVICE_IP:8080/d20
```

**View the statistical analysis:**
```bash
curl "http://YOUR_DEVICE_IP:8080/analysis?pin=123456"
```

## How to Use

1.  **Print the Housing:** Download the model from Thingiverse and 3D print it.
2.  **Assemble the Hardware:** Install a standard 5V laser module and your Android phone into the housing.
3.  **Install the App:** Get the Android application from the official GitHub repository: [https://github.com/cperos-xr/LaserSpeckleRNG](https://github.com/cperos-xr/LaserSpeckleRNG)
4.  **Connect to Wi-Fi:** Ensure the device is on the same Wi-Fi network as the computer you want to access the API from.
5.  **Focus the Laser:** Use the on-screen slider to manually adjust the camera's focus until the speckle pattern is sharp, maximizing the entropy.
6.  **Roll the Dice:** Use the on-screen buttons or the API to generate true random numbers.
