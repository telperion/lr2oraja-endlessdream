# Fourteenizer

**Fourteenizer**, also stylized as **14izer**, is an algorithm designed by Telperion that creates double (14K2S in BM controller layout) charts from existing charts by rearranging the existing objects in playable lanes.

## Guiding principles

### Glossary
- **TT**: turntable, scratch
- **P1**: the left side of the controller
  - beatoraja BMS model: lanes 0-6 -> keys L to R, lane 7 -> TT
- **P2**: the right side of the controller
  - beatoraja BMS model: lanes 8-14 -> keys L to R, lane 15 -> TT
- **LN**: long note, a.k.a. "noodle"
- **SN**: short (non-long) note, a.k.a. "rice"
- **BSS**: long note in a scratch lane

### General structure
The algorithm has the following priorities:
1.  Sensible patterning
    1.  Never place any notes on the same side as a BSS.
    1.  Always place LN on the same side as other active LN.
        - Not necessary to create sensible patterns; artificially placed limitation to reduce the need for chart lookahead.
    1.  Never place any TT on the same side as an active LN.
    1.  `Avoid` placing SN on the same side as a TT, and vice versa, `within a certain timeframe`.
    1.  `Prefer` placing SN on the side away from any active LN.
    1.  If, after following all the above rules, `too many notes` would be placed on one side, try again with any TT SN retargeting key lanes.
    1.  If, after following all the above rules, `too many notes` would be placed on one side, send the extras to the background.
    1.  `Prefer` comfortable hand positions when arranging notes in key lanes. Account for active LN when filtering hand positions.
    1.  `Avoid` placing jacks (successive notes in the same lane `within a certain timeframe`).
1.  Maintain structure of original chart
    1.  Notes originally mapped to the turntable should stay mapped to a turntable as often as possible.
    1.  Separation of multiple instruments in one phrase between sides can provide more playable realism than simply scattering notes randomly between sides or within one side.


## Configurable parameters

Portions of the algorithm decided on a random basis are controlled by sigmoid transitions with two parameters: an **inversion time** `T`, and a **zero offset** `z`. The sigmoid function is designed so that `s(0) = 10^(-z)` and `s(T) = 1 - 10^(-z)`.
- **H-ness of Random**: There are two options for mapping the notes that roughly correspond to lane-shuffle random, often referred to as RAN, and a somewhat smoothed-over note-shuffle random similar to H-RAN in console versions. This parameter is controlled by time, but the inversion time of the sigmoid is scaled by the similarity in the filename of the new note vs. its predecessor, calculated by a proportional Levenshtein distance.
  - **RAN**: Recall where the previous note originating from this lane was mapped to, and repeat that mapping if possible. If repeating is not possible, fall back to the **H-RAN** strategy.
  - **H-RAN**: Use the algorithm described above to shuffle each note independently of its history in the origin chart.
- **Jack prevention**: Prevent key notes from being placed too close to the previous note in a lane. The input to this parameter is the time since the last note in the potential destination lane.
  - Only applied if one of the notes in question was assigned using **H-RAN**.
- **Murizara prevention**: Prevent key notes from being placed too close to TT on the same side, and vice versa. The input to this parameter is the time since the last note in the potential destination lane.
  - When checking a TT note, if murizara prevention triggers for `too many` potential destination lanes (see **Scratch reallocation**), the scratch is reallocated to a key lane using **H-RAN**.

In addition to these parameters, there are also two controls that contribute to side preference:
- **Scratch reallocation**: If the algorithm would distribute more than this many notes to one side while avoiding murizara, the scratch is reallocated to a key lane using **H-RAN**, and the algorithm is re-run for the new collection of notes.
- **LN avoidance factor**: The threshold for placing a note on the given side is `e^(-LN)`, where `L` is the LN avoidance factor and `N` is the number of active LN on the same side.


## The algorithm

> NOTE: when comparing sides by trigger counts, if there is a tie, choose randomly between the two.

### Allocating notes to sides
1. Place each TT LN (BSS).
    1. If either scratch lane currently contains an active BSS:
        - map this LN to a key lane instead
    1. If both sides currently contain an active key LN:
        - map this LN to a key lane instead
    1. Otherwise:
        - map to the TT lane on the side that triggers fewer murizara preventions
1. Place each key LN.
    1. If there are any currently active LN:
        - choose a side that has at least one active LN
        - increment its reception count
    1. Otherwise:
        - choose the side that triggers fewer murizara & jack preventions
        - increment its reception count
1. Place each TT short note (SN).
    1. If there is a currently active BSS:
        - remove that side from the pool of selectable lanes
    1. If all available sides currently contain an active key LN:
        - map this SN to a key lane instead
    1. Otherwise:
        - map to the TT lane on the side that triggers fewer murizara preventions
1. Place each key short note (SN).
    1. If there is a currently active BSS:
        - remove that side from the pool of selectable lanes
    1. If there is a currently active key LN:
        - choose the available side that triggers fewer LN avoidances
        - increment its reception count
    1. Otherwise:
        - choose the side that triggers fewer murizara & jack preventions
        - increment its reception count

### Resolving reception count
1. If the total key notes on either side (counting both LN and SN) exceeds the scratch reallocation threshold:
    - choose sides for the TT notes as if they were key notes of the corresponding type, using the side allocation algorithm above
1. If the total key notes on either side (counting both LN and SN) exceeds the scratch reallocation threshold:
    > - TODO: map any extra notes to the background lanes?

### Mapping notes to lanes

For each incoming key note:
1. Filter the key note history to:
    - notes in selectable lanes
        - selectable sides for that note
        - at least one five-finger favorability combo matches on that side
    - that were mapped without H-RAN
    - and whose source lane matches the incoming note's source lane
1. Run the RAN vs. H-RAN trigger for each note in that filtered history.
1. If on any note, the trigger doesn't fire:
    - use the same mapping as that previous note
    - remove that lane from the selectable lanes
    - continue to the next incoming note

For each remaining key note:
1. Choose among the selectable lanes using the flattened five-finger favorability PDF across selectable sides for that note.