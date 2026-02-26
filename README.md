# SpeckleRng - A True Random Number Generator (TRNG) Appliance

SpeckleRng transforms a standard Android phone into a dedicated hardware appliance for generating **true random numbers**. Unlike pseudo-random number generators (PRNGs) found in most software, SpeckleRng uses a physical, unpredictable process to generate entropy, making its output fundamentally unpredictable and suitable for security-sensitive applications.

## True Randomness vs. Pseudo-Randomness

Standard random number functions in programming languages (`Math.random()`, `random.range()`) are **Pseudo-Random Number Generators (PRNGs)**. They use a deterministic algorithm seeded with an initial value. This means their output is predictable and will repeat if you use the same seed, making them unsuitable for security applications.

SpeckleRng, on the other hand, is a **True Random Number Generator (TRNG)**.

- **Entropy Source:** It derives its randomness from a physical, unpredictable process: the chaotic patterns of **laser speckle** captured by the phone's camera sensor. This real-world entropy source is non-deterministic.
- **Predictability:** The output is fundamentally unpredictable. Even with complete knowledge of all previous numbers, you cannot predict the next one.
- **Use Cases:** This makes it essential for cryptography, scientific modeling of chaotic systems, security protocols, and any application where genuine unpredictability is paramount.

## Features

- **Foreground Service:** The camera analysis and web server run in a foreground service, ensuring the appliance runs continuously, even when the app is in the background.
- **Embedded Web Server:** A Ktor server runs on port 8080, exposing a RESTful API to access the random numbers and status.
- **Live Monitoring:** A simple web interface provides a real-time view of the camera's region of interest (ROI) and the frame-to-frame difference that is used to generate entropy.
- **Statistical Analysis:** A PIN-protected web page provides a comprehensive statistical analysis of all generated random numbers, including a Chi-Squared test and a visual distribution chart to verify the quality of the randomness.
- **Glitch Detection:** The system automatically detects and isolates glitched frames (e.g., all-black frames from a sensor stall) to prevent them from corrupting the random number log. Saved glitch images can be viewed from a link on the analysis page.
- **Manual Focus Control:** A slider on the main screen provides absolute, granular control over the camera lens's focus distance, allowing you to precisely focus on a close-up entropy source like a laser speckle pattern.
- **Auto Power Save:** To ensure 24/7 operation, an optional "Auto Power Save" mode will automatically dim the screen to its lowest brightness when the battery is low, and restore it when the battery is recharged.
- **Persistent Logging:** All valid, non-glitched random number receipts are saved to a log file on the device for later analysis.

## API Endpoints

- **`/`**: The main HTML interface, showing a live view and status.
- **`/status`**: Get a raw JSON object of the current status (FPS, uptime, entropy health, etc.).
- **`/rng?min=X&max=Y`**: Generate a new random number within the specified range.
- **`/log?pin=PIN`**: View the complete, raw log of all generated receipts.
- **`/analysis?pin=PIN`**: View the statistical analysis and randomness report.
-- **`/clear_log?pin=PIN`**: Delete all logs and duplicate frame records to start a new session.
- **`/duplicates?pin=PIN`**: View links to any saved glitched frames.
- **`/help`**: Get a plain text list of all available API commands.

## How to Use

1.  **Build and Run:** Install the app on a dedicated Android device.
2.  **Connect to Wi-Fi:** Ensure the device is on the same Wi-Fi network as the computer you want to access the API from.
3.  **Find the IP Address:** The app will display the device's local IP address on the screen.
4.  **Access the API:** Use a web browser or a tool like `curl` to interact with the API endpoints listed above.
5.  **Generate Entropy:** For the best results, point the camera at a source of visual noise, such as a shimmering laser speckle pattern on a matte surface. Use the on-screen slider to manually adjust the focus until the pattern is as sharp as possible, maximizing the detail and thus the entropy.
