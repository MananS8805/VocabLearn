# Changelog

## 1.1 — Glossa

**Renamed** from WallLearn to Glossa, with a new launcher icon (three bars, the middle one
highlighted). The package id is unchanged, so updating keeps your words and settings.

**New**
- Bottom tab bar: Today, Words, Essays, Themes. Tabs keep their state.
- GRE essay reader: 40 model essays plus your own; Read and Listen modes with a lyrics-style
  highlight; notes on strongest points, vocabulary and structure; add vocabulary to your lock
  screen in one tap. Five voices, speed and pitch.
- App looks: Dark (default), Light and Bauhaus, switchable in the Themes tab.
- 11 lock-screen themes (10 new, plus Bauhaus), with live previews.
- Online word lookup (Wiktionary) for words missing from the offline dictionary, opt-in and saved
  on the device. Adds the `INTERNET` permission.
- "Next word" types the word in letter by letter.

**Fixed**
- Bauhaus look crashed the app at launch (an inline colour in a theme where Material needs a
  colour resource). Covered by a new test.

**Changed**
- The scenic "Dusk" app background (with twinkling stars) was removed in favour of Dark/Light.
  A saved Dusk setting opens as Dark.

## 1.0

Initial release as WallLearn: rotating GRE words on the lock screen, offline WordNet dictionary,
add your own words.
