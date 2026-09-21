# Rapid Companions

Rapid Companions is a client-side RuneLite plugin for choosing a cosmetic pet
that follows the local player. Other players cannot see the companion.

## Version 1.0.0

This build adds fine-grained percentage controls for pet size and follow speed,
moves the brown-and-gold paw safely inside the sidebar icon frame, and adds an
optional collision-aware combat flee mode with an adjustable safe distance.
Pet movement now keeps a short path buffer and carries fractional speed between
client ticks, removing the brief stop at every tile.
Following now uses a bounded collision-map search so companions can route around
walls, furniture, and other scenery instead of repeatedly walking into them.
Follower routes are now replaced when the player turns, preventing stale queued
waypoints from making the pet circle or run past its owner. The sidebar is now
named Companion Menagerie.
Companions now target the player's recorded previous tiles exactly rather than
choosing an arbitrary tile above or below the player.
The player trail is now ordered by footsteps, including intermediate running
tiles, so bends and loops cannot make the pet select the wrong part of the path.
Combat fleeing is now activated by actual hitsplats and held briefly between
hits. Merely interacting with an NPC can no longer send the pet running away.
Normal following once again uses the reliable direct tile-step logic. The wider
route search is limited to a one-tile recovery only after repeated blockage.

Custom pets can now be added by NPC ID from a dedicated section below
Favourites. Their model, name, size, scale and recolours come from RuneLite's
NPC definition; animations are learned and remembered when that NPC is loaded
nearby. Custom entries and the selected custom pet persist between sessions.
Each custom pet can be given its own persistent name while it is added. A live
50%-200% pet-size slider now sits between Custom Pets and Companions in the
Menagerie sidebar.
The custom-pet form now uses full-height labelled fields so entered text remains
clearly visible. The quick summon button displays custom pet names in place of
the generic NPC label, fitting longer names across two lines.
Saved custom pets are now restored only after RuneLite reaches the logged-in
state, preventing NPC-definition startup failures. Partial startup cleanup is
also guarded so the plugin can always be enabled again safely.

- Pet selector and favourites panel
- Adjustable follow distance from 1 to 5 tiles
- Route-history following for better corners
- Collision-aware movement
- Configurable catch-up distance
- Recovery after teleports, region loading, login and world hopping
- Reliable looping for idle, walk and run animations
- Dom, Gull/Gulliver, Bone Squirrel and Soup pet data
- Menagerie-style companion cards and selected-pet highlighting
- Starred favourites section at the top of the sidebar
- Fixed the large catalogue resizing the game viewport when opened
- Compact three-column, icon-only pet and favourite grids
- Favourites are hidden from the main catalogue to avoid duplicates
- Collapsible companion-thrall section moved below the pet catalogue
- Movable quick summon and dismiss button using the selected pet icon
- Bear-paw sidebar icon
- One-way follow-distance leash: pets catch up but never back away from you
- Brown bear-paw sidebar icon with a gold outline
- Working pet size multiplier, including pets with neutral base scaling
- Adjustable 1x-4x collision-aware follow and pathing speed

To summon the selected companion, click the in-game Call Follower whistle while
you do not have a real follower. The plugin changes the resulting message and
spawns the local companion.

## Local testing

1. Open this project folder in IntelliJ IDEA Community Edition.
2. Set the Project SDK and Gradle JVM to Java 11 (Eclipse Temurin).
3. Run `gradlew.bat clean test`.
4. Run `gradlew.bat clean runClient`.
5. Log in, select a pet from the Rapid Companions side panel and click the
   in-game Call Follower whistle.
6. Test walking, running, corners, stairs, teleports, world hopping and each
   follow-distance setting.

## Attribution

This project began as a permitted derivative of Mrnice98's Companion Pet Plugin
and retains its BSD 2-Clause licence and copyright notice. Movement and pathing
have been modified for Rapid Companions.
