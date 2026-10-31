# SPEC: Open Factor intro, storyline A "One Set"

GOAL:    a lifter finishes the film thinking "logging is quick and calm, and it's free", and knows where to get it.
OUTPUT:  HyperFrames, 1920 x 1080, 30 fps, MP4.
TIME:    48.0 s x 30 = 1440 frames. No music bed (no HeyGen sign-in); bundled UI sound effects only.
LOOK:    ground #F5F5F5 (the app's light background), ink #131313, muted #55555B; accent #18785A (the app's
         light "complete" green) on one word only ("Logged"). Palette floods use the app's own primaries:
         Midnight #9DB8FF, Ember #F5A54A, Moss #CFE6C4, Mono #FFFFFF. End card ground #131313, ink #FFFFFF.
         Display DM Sans 700, 104 px, -2% tracking; hero number 300 px; sub-lines DM Sans 400, 40 px.
         Medium: the real app (dark charcoal UI) inside a plain phone body (#0A0A0A, 60 px radius, 12 px bezel)
         on the light studio ground; contact shadow 0 40 80 rgba(0,0,0,.22). No grain, no vignette.
OBJECTS: the phone (real screen recordings of Open Factor 0.9.0 with demo history), the OF app mark (from
         res/drawable/ic_open_factor.xml, exact geometry).
MOTION:  arrivals expo.out / power4.out; camera power2.inOut / power3.inOut 0.5-0.8 s; whips power3.in then
         power3.out 0.15 s each side; titles per word blur 6 px -> 0 in 0.13 s, 0.067 s apart, no translate;
         name per letter 0.05 s apart; exits blur-out 0.18 s.
SHOTS:   (film s; source = recordings in assets/footage, video time ≈ logged tap + 0.45 s)
  0.0-4.25   HOOK. Camera at 1.78x on set rows + keypad. "1 0 0", Next, "5", Done, swipe right: green strip.
             Titles: "One more set" -> "Type it / Swipe it / Logged". Key ticks, whoosh on swipe, pop on green.
  4.25-8.6   Whip pull-back to the whole phone: bench with 3 sets logged. Title "Open Factor" per letter,
             sub "A free workout log for Android".
  8.2-14.0   Push to Guide; tap; sheet rises; demo video plays; angle switches. "Watch the movement" /
             "Video guides for 619 exercises".
  14.0-15.9  Hard punch-in cut on the Muscles tab tap: anatomy map. "See what it works".
  15.9-20.8  Whip to the row: menu, Swap exercise sheet, pick Pendlay Row, "Just today?" dialog.
             "Rack taken? / Swap it", sub "Same muscles, the equipment you have".
  20.8-24.4  Dip cut to Finish confirm -> Workout Complete summary, push in. "Every set, saved".
  24.4-30.8  PAYOFF. Dashboard: push to the 47-sets rings (1.9x), pull back as it scrolls to the muscle map.
             "Watch your week / add up".
  30.8-34.4  Library scrolls; "1,345" lands huge with "exercises".
  34.4-39.0  Appearance: each palette tap floods the ground with that palette's colour. "Make it yours" /
             "Five color themes".
  39.0-42.3  Claim on the light ground, phone holds the dashboard: "Free / No account / No ads",
             sub "Your training stays on your phone".
  42.0-42.9  Morph: the phone crops to a square, shrinks into the app-icon tile; ground floods #131313.
  42.8-48.0  END CARD: the O rises, the F wipes in, "Open Factor" per letter, then "Free and open source for
             Android", "github.com/aarsh21/open-factor", credit "Exercise videos: thanks to Jeff Nippard". Hold.
BANNED:  dark void with small floating things; glow, particles, gradients, glass; everything fading + sliding up;
         titles that move after landing; click rings; mock UI of any kind; logo docked in a corner; three cards.
QA:      0.0 (thumbnail: real UI + light ground), 2.3 (keypad digits readable), 4.2 (green strip), 6.8 (name),
         10.5 (video large), 14.5 (anatomy), 17.8 (swap sheet), 22.8 (summary), 26.2 (rings 47), 32.5 (1,345),
         35.8 (Ember flood), 40.8 (claim), 42.5 (morph mid), 45.5 (end card complete).
