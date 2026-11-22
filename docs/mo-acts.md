# Writing an act for Mo

An act is one little thing Mo can be up to: whistling, staring at an empty bowl, a tantrum. The day and the part of the day pick one that fits what is going on (`domain/mo/MoOccasion.kt`), so he is a new sight every day. This page is everything needed to write one. Read `docs/mo.md` first: it says who he is, and an act that breaks it is not Mo.

## The contract

An act never draws Mo. It extends `MoAct` (`feature/mo/acts/MoAct.kt`), says how he holds himself, and draws only what is around him or in his hands. The kit (`feature/mo/MoKit.kt`) draws him from that on whichever body his state gives him, which is what keeps every act looking like the same character.

```kotlin
private object Whistling : MoAct("idle_whistling", MoOccasion.Idle) {
    override fun pose(t: Float, v: MoVariant, body: MoBodyKind): MoPose = moIdle(t).copy(...)
    override fun DrawScope.world(t: Float, v: MoVariant) { /* behind him, does not move with him */ }
    override fun DrawScope.held(t: Float, v: MoVariant, rig: MoRig) { /* in his hands or on him, moves with him */ }
    override fun DrawScope.foreground(t: Float, v: MoVariant) { /* passes in front, does not move with him */ }
}
```

`feature/mo/acts/CoreActs.kt` holds one finished act for each kind of moment. Read it before writing anything; it is the pattern.

- **`id`** is `occasion_snake_case_name`, unique, and never changes after release.
- **The object's name is unique across all act files.** Private objects in one package still share class names, so a second `BraveFace` in another file does not compile: prefix it with the occasion when the plain name is taken.
- **`intensity`** says how big the feeling is, and the day picks acts whose intensity fits what is going on, so he starts small and only rarely goes all the way. `Mild` is a small gesture he could keep up all day: a tapping foot, a sideways glance, a pleased hum. `Medium` (the default) is plainly emotional. `Strong` takes him over completely: red and spitting fire, leaping, sobbing. Most acts of an occasion should be Mild or Medium. A Mild act keeps the Warm palette and small motion; only Strong anger wears Rage, and only Strong fear wears Pale.
- **`pose`** starts from `moIdle(t)` (or `moIdle(t, energy)`), so he keeps breathing, swaying and blinking, and changes only what the act is about: eyes, brows, mouth, arms, legs, palette, tilt, offset, flare, shake.
- **Everything is a pure function of `t`**, the seconds since he appeared, built from `wave`, `pulse`, `cycle`, `twitch`, `ease`, `sin`. No state, no randomness, no clocks. It must loop forever without a visible seam, and nothing may grow without bound.
- **At `t = 0` the act already looks complete**: that frame is what the widget and the notifications show. A prop that only appears later is a bug.
- **`v: MoVariant`** makes the same act a new sight another day: a colour, a count, a side. Use `v.of(listOf(...), salt)`, `v.pick(n, salt)`, `v.chance(percent, salt)`, `v.between(a, b, salt)` with a different `salt` for each independent choice. Every act should have at least one such choice.
- **Bodies.** Most acts are for `MoBodies.Burning` (the tall flame and the low Dimming one) and must look right on both: take every position from the `rig` or the body, never from a hard-coded face coordinate. Cold acts use `setOf(MoBodyKind.Droplet)`; the droplet holds his core right under his mouth, so only a small mouth fits there (Frown, Flat, a small Smile or Oh, Wavy, Zigzag), and `scripts/mo_acts.py poses` reports one that reaches into the core as `under-core`. Husk acts `setOf(MoBodyKind.Husk)` (the Husk ignores the pose: he does not move, only the world around him does).

## Space

His design space is 230 wide with the centre at x = 115 (`CENTRE`) and his feet on y = 425 (`FEET`); up is smaller y. The tall body's tip is near y = 82, its eyes at y = 284, x = 71 and 155, its mouth near (113, 322), its shoulders at y = 330, 92 either side of the centre. At rest, Blazing has the tightest crop, about x = -45 to 275 and y = -1 to 431. Leave room for his breathing and for the extremes of each gesture. The same picture shows more of a smaller Mo’s surroundings. Its top is near y = -76 when Steady and y = -144 when Dimming. What hangs into the picture from above, such as a thread, a lamp's rod or a curtain, starts at `ABOVE_THE_PICTURE`. Keep the support fixed there while the object swings. Draw it in `world` or `foreground`, where the kit clips it to the top of the picture. `threadFromAbove` draws a line to the hanging object. What crosses the floor, a snail, a tumbleweed, a line of ants, must come in from the edge of the picture he is in and leave at the other: override `world(t, v, picture)` or `foreground(t, v, picture)` in place of the plain ones and use `picture.left` and `picture.right`, which are -45 and 275 only when he blazes. Things standing on the floor sit on y = 425 and get a `floorShadow`. He is about 215 wide at the base, so floor props beside him start around x < 0 or x > 230. A prop that needs more floor than those 45 units gets it from him: stand him aside with the pose's `offset`, about 30 away from it, as the grill and the kettle acts do. A prop that half disappears behind him, with its things lying on his body, is worse than a Mo who stands off centre.

`MoRig` gives the real positions after the pose: `leftHand`, `rightHand`, `leftEye`, `rightEye`, `mouth`, `tip` (the top of his flame, which sways), `leftFoot`, `rightFoot`, plus `body` and `palette`. Left and right are the viewer's.

## What the kit gives you

- **Eyes** (`MoEye`): `shape` (Round, Happy, Shut, Cross, Spiral, Star, Heart), `gaze` (-1..1 each axis), `iris` (0.76 normal, 0.5 startled, 0.3 terrified), `lid` (+ glare, − sad), `lidDrop` (0..1 sleepy), `open`, `scale`, `tear`.
- **Brows** (`MoBrows`): None, Angry, Worried, Raised, Flat, with `lift` and `skew` (one raised).
- **Mouths** (`MoMouth`): Smile, Grin, Frown, Flat, Oh, Gritted, Beam, Wavy, Zigzag, TongueOut, Drool, Chew, Yawn, Whistle, Pout, Smirk, Hidden.
- **Arms** (`MoArm(reach, bend)`): `reach` is the hand relative to its shoulder, x positive outward, y positive down. `bend` bows the elbow away from his body on an arm that hangs or is raised; on an arm that runs level, such as one that brings a mitten to his mouth, the elbow hangs below it instead, so give such an arm a bend or it is a rod across his face. Presets: Down, Up, Out, Hip, Cheek, Front, Flex. `armsInFront = true` draws them over the body, for hands on the face or belly, and `MoArm(..., inFront = true)` does it for one arm alone. An arm that reaches across his body behind it is hidden, hand and all.
- **Mittens**: the kit draws his hand before the act draws what he holds, so the thing lies on his hand. Draw `grippingMitten(rig, side)` after it, or `mitten(rig, at, side)` for a hand somewhere else, and it sits in his grip. Never draw a mitten by hand: the cold tip of a Dimming Mo's is part of it. That slate tip reads as a hole when the mitten lies on his face, and as a pupil on a shut eye: give such an arm `warm`, raised as the hand comes up, and pass the same amount when the mitten is drawn again.
- **Legs** (`MoLegs(left, right, sitting)`, `MoLeg(lift, swing)`). `sitting` brings his body down onto the floor and puts his feet in front of it; what he sits on that is higher than the floor, the act lifts him onto with `offset`.
- **Palettes** (`MoPalettes`): Warm (default), Fading, Rage, Pale, Queasy, Golden, Slate. Never build your own.
- **Body**: `sway`, `flare`, `squash`, `tilt` (degrees), `offset`, `shake`, `blinks`.
- **Fading**: a thing made of several shapes fades as one inside `fading(alpha, bounds) { ... }`; with its own alpha on each shape the joints show through. `shape` does this by itself.
- **Drawing**: `limb`, `oval`, `curve`, `polyline`, `block`, `chunk` (a solid thing with a darker ledge, like a Momentum button), `shape`, `sparkle`, `flameTongue`, `puff`, `drop`, `tear`, `smoke`, `floorShadow`, `angerMark`, `cobweb`, and plain `drawCircle`, `drawRect`, `drawRoundRect`, `drawPath`.
- **A tool with something on it** must still show. What lies on a thing he holds is drawn in `foreground`, after `held`, and covers it: a spatula whose blade faces us is hidden by the patty on it, and only a stick is left. Turn the tool so it shows past its load, as the grill act lays the blade level under the food, and let it face us when it is empty and held up.
- **What is inside something** is cut off by its edge and has no outline of its own. Teeth fill the mouth and the lips cut them; the water in a brimming eye lies along the bottom of it and the eye's rim holds it. A block of teeth with its own corners inside an arched mouth, or an oval of water laid across an eye and out over its rim, reads as painted on. Clip to the outer shape (`clipPath`) or cut the inner one out of it (`Path.combine`), as `grittedTeeth` and the crying act do.
- **Things of his that more than one act shows** are drawn once: `toothbrush` (`MoToothbrush.kt`) and `trainer` (`MoTrainer.kt`), his running shoe. Draw his with these, never a second one by hand. On his feet a shoe points outward, as his feet do: his leg comes down inside of the middle of his foot, where the shoe has its ankle. Keep `MoLeg.swing` constant while he wears shoes, because a swing moves the foot twice as far as the leg and takes the leg out of the shoe.
- **Colours**: only the `MoProp…` tokens and Mo's own tokens from `core/ui/theme/MoPalette.kt`. No colour literal and no `Color.Something` anywhere else: a guard test fails the build. `.copy(alpha = …)` on a token is fine.

## Looking at it

An act is judged by its pictures: code that compiles can still draw nonsense. `scripts/mo_acts.py` draws them on the PC with the app's own code, so no phone is needed.

- `scripts/mo_acts.py moments level_ loss_ event_ --language de --phone narrow --font-scale 1.3 --step-ms 32` films the actual screens with the Compose clock held. Each folder contains timed PNG frames and an animations-off still. Use `--phone tall` for 412 × 892 dp or `narrow` for 360 × 640 dp. Use `home_` for the hop and workout burst, `rewards` for the icons at several sizes and `share_` for the original share images.
- `scripts/mo_acts.py sheets idle_whistling` renders a review sheet: eight moments, the top row in the act's brightest state in one variant, the bottom row in its dimmest in another. Every cell is cut at his box, as the widget's picture is.
- `scripts/mo_acts.py variants idle_whistling` shows the act in variants 1 to 24 at half size, each with its number: the shapes, colours, counts and sides a day can choose. A sheet shows two variants, so an act with three shapes has one that no sheet shows. Look here before calling an act reviewed, then film each shape, count and side with `film --seed`.
- `scripts/mo_acts.py film idle_whistling --from 2 --to 3 --frames 12` shows one stretch frame by frame, one row per state. Use it for anything that moves: a loop's seam, a prop arriving.
- `scripts/mo_acts.py audit idle_` measures what a still picture cannot show: what leaves his box (`still` for the first moment, `overflow` for any), what jumps from one instant to the next (`pop`), and what is drawn where his neglected things lie (`floor`). The audit also reports facial changes in the pose (`face`) and motion it can resolve at finer intervals (`quick`). Inspect the surrounding frames. A bubble should burst.

- `--close face` on a sheet or a film fills each cell with his eyes and mouth, and `--close body` with him from the shoulders down. Only that close can a mouth, a tongue, a hand on a prop or an arm against his body be judged; four frames of a close-up are as wide as a sheet.
- `scripts/mo_acts.py poses idle_` reads the poses without drawing them: a hand hidden behind his body (`hidden`), an arm lying on an eye or his mouth (`on-eye`, `on-mouth`), arms that jump between behind and in front (`flip`), and an arm longer than he can reach (`long`).
- `scripts/mo_acts.py kit` draws every mouth, eye and arm of the kit on the three bodies an act can pose (the tall flame, the low one and the droplet), and `scripts/mo_acts.py classic` his looks without an act, the Husk among them. A film with `--state` skips an act that does not play in that state.

Look at both rows. The first cell of each row is the widget picture for that state and variant. Use a film to check states between the two rows and changes that fall between the eight moments. Use a separate `--out` directory for each version you compare.

## Right after a save

For about nine seconds after a task is logged he does exactly that: reads, brushes, runs, weighs himself. Those acts have the occasion `Doing` and name their task: `MoAct("doing_reading_speed_reader", MoOccasion.Doing, saveAct = MoSaveAct.Reading)`. The clock never draws them. Home plays one when a task becomes kept for the day, and which one moves on with the day: a task kept every day is acted out differently from one day to the next. Every activity has an act for both burning bodies. He only acts a save out while he burns, and neither in a tantrum nor in the ice, and a sore mouth keeps him from brushing; a save he does not act out leaves alone whatever is playing, his day's act or an earlier save he is still acting out, and he hops for it all the same. A timer shows one act for as long as its session runs: the act of that activity that sets `forASession`, a quiet loop that can be watched for an hour, and failing that the first by id. They are short and joyful, must read within the first second, and never need more than those nine seconds to make their point.

On Home, with animations on, every save is also met with a hop, or a burst of fire for a workout. While he acts the save out, only he leaves the floor, with what `held` draws; `world` and `foreground` stay where they are, so a thing he stands on belongs in `world`. A thing that stands on the floor and still has to be drawn in `held`, because his mitten lies on it, is drawn `rig.lift` lower and so stays put while his hand comes away (`doing_reading_turn_the_page` and its book on a stand). That only works in a pose with no `tilt` and no `squash`, since `held` draws inside both. Only the acts for a save meet this: when he does not act the save out, his day's act hops with him as one whole picture. Which of the two it is only ever changes while he is on the floor, so a hop that is under way keeps to the one it started in even if what he plays changes in the middle of it. A save that reaches him while his whole picture is in the air is acted out from the moment he stands: what it shows stands on the floor, and shown any sooner it would come down with him. A save that meets him in the air and lifts him the same way takes him on from where he is: a rise takes only the share of its time that the way left takes, so from the top he comes straight down. One that lifts him the other way has him touch down first, again in the share of the time that the way left takes, and then hop, or burst into fire on the floor for a workout. If the phone switches animations off while he moves, he is put on the floor at once.

## In bed

Past the hour he should be asleep, Sleepy turns `Strong` and he is in bed (`feature/mo/acts/MoBed.kt`). Every act in bed uses the one shared bed, seen from its foot end: draw `bedBehind()` last in `world`, start the pose from `moInBed(t, body)` instead of `moIdle`, and draw `bedBlanket(t, v)` first in `foreground`. The blanket's edge is `BED_LINE`; everything of him below it is hidden, his mittens rest on it, and `pulledUp` raises it. `sleepy_bed_tucked_in` in `CoreActs.kt` is the pattern. These acts set `ownsTheFloor` and switch `sparkles` off. A Sleepy act that is not in bed is `Mild` or `Medium`.

## In the ice

Start a Frozen pose from `frozenRest(body)` so the droplet keeps both mittens around his core. The kit holds Mo and anything in `held` at their first moment. Draw distant scenery in `world` and falling snow or feathers in `foreground`. For a visitor touching the block, override `DrawScope.atIce(t: Float, v: MoVariant, ice: Rect)`. It draws on the outside, after the glint. Place the contact from `ice.left`, `ice.right` or `ice.top` in design units. The cub in `FrozenActs1.kt` plants its tongue there while its head pulls away. Check the widget frame and the full reach on all five states.

## What makes it Mo

- Flat, saturated shapes with hard edges. No gradients, no outlines, no texture, no glow. Solid things get a darker ledge (`chunk`).
- **No text, letters, digits or punctuation marks** anywhere, including question and exclamation marks and "Z" for sleep. Say it with a picture.
- **No hats**, and nothing that hides both eyes, the mouth or the flame tip for the whole loop. Things he wears sit below the tip.
- **Things near him do not burn.** He is warm, not dangerous.
- **No wrong incentives** (`docs/mo.md`): junk food is never what he craves, dreams of, or wins; the scale is never something to fear; a prop that touches a risk, such as a celebratory drink, reads as its harmless version. Nice things are fine, and he is never preachy.
- **Nothing reads as smoking.** A stream of clouds from his mouth beside a mitten or a stick held to it is a smoker's picture, whatever the act meant. A shh is a finger across shut lips, with no breath.
- **He is ethically vegan** (`docs/mo.md`): no fishing and no hunting. Food may be anything that could be vegan, such as a burger or a sausage, but never something that can only come from an animal, such as a drumstick or a fish.
- Smug, sarcastic, dramatic, and angry are all fine when they point at a task left open, like the Duolingo owl. He never makes fun of the user, never of a logged value such as a weight or food quality, and never of Substances.
- Something cheerful in his mittens is fine in any state, such as a bauble at Christmas while he dims; the contrast can be the joke, as long as his face and body still say how he is. Cold and Husk acts are quiet and sad; the Husk does not look at you.
- One idea per act, readable at the size of a thumbnail, within a second. If it needs explaining, it is two acts or none.
- Motion is small and alive, not a machine: ease it, offset the parts from each other, and let something rest.
