# Deadly Silverfish (Immortal Snail Plugin)

A Minecraft plugin that implements the "Immortal Snail" scenario: an unstoppable, immortal silverfish that constantly pursues players. If it touches you, you die instantly!

## Features

- 🐌 Spawns an immortal, indestructible silverfish that relentlessly pursues players
- 🏗️ The silverfish can break blocks and build paths to reach players
- 🌍 Works across all dimensions (Overworld, Nether, End)
- 🏃‍♂️ Silverfish will teleport closer if it falls too far behind
- ⚡ Custom death messages and effects when caught
- 🔨 Smart pathfinding with block breaking and bridge building
- 🎯 Persistent tracking that survives server restarts
- ⚖️ Balanced movement speed matching player walking speed

## Installation

1. Download the latest release from the [releases page](link-to-releases)
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. The plugin will automatically create necessary configuration files

## Building from Source
bash
git clone https://github.com/ikeshaviyer/The-Immortal-Creeper.git
cd The-Immortal-Creeper
mvn clean package

The compiled JAR will be in the `target` directory.

## Usage

The plugin works automatically after installation:
- A silverfish will spawn near a random player
- If the targeted player dies, the silverfish will choose a new target
- The silverfish will follow players across dimensions
- Players cannot kill the silverfish - they can only run!

## Death Messages

When caught by the snail, players might see one of these custom death messages:
- "Player was finally caught by the Immortal Snail"
- "Player should have kept running from the Immortal Snail"
- "Player's immortality was revoked by the Immortal Snail"
- And more!

## Permissions

This plugin currently doesn't use any permission nodes - it affects all players equally.

## Configuration

No configuration needed! The plugin works out of the box.

## Contributing

1. Fork the repository
2. Create a new branch (`git checkout -b feature/improvement`)
3. Commit your changes (`git commit -am 'Add new feature'`)
4. Push to the branch (`git push origin feature/improvement`)
5. Create a Pull Request

## Known Issues

- The silverfish may occasionally get stuck in complex terrain (will teleport if stuck too long)
- Some block types might cause unexpected behavior during pathfinding


## Acknowledgments

- Inspired by the "Immortal Snail" thought experiment/meme
- Built using the Spigot/Bukkit API


---

Made with ☕ and 🐌
