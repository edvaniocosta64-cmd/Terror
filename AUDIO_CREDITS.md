# Créditos e Licenças de Áudio

Todos os efeitos sonoros e vozes integrados em `app/src/main/res/raw/` usam licenças permissivas verificadas:

## Efeitos Sonoros e Ambiente
1. **Footsteps on different surfaces** (`sfx_step_wood_1..4`, `sfx_step_tile_1..4`, `sfx_granny_step_1..3`, `sfx_step_dirt_1..4`)
   - Fonte: OpenGameArt (`https://opengameart.org/content/footsteps-on-different-surfaces`, por `congusbongus` / `avas_ter`)
   - Passos em madeira, piso e botas derivados de `swuing` e `ceberation` (Freesound) — Licença **CC-BY 3.0**
   - Passos em terra/cascalho derivados de `Ali_6868` (Freesound) — Licença **CC0 1.0 (Domínio Público)**

2. **Horror Sound Effects Library** (`sfx_horror_sting`, `sfx_ambush_surprise`, `sfx_scared_breath_1..2`, `sfx_distant_noise`)
   - Fonte: Little Robot Sound Factory via OpenGameArt (`https://opengameart.org/content/horror-sound-effects-library`)
   - Licença: **CC-BY 3.0**

3. **50 RPG Sound Effects (Kenney RPG Audio)** (`sfx_door_close`, `sfx_door_slam`, `sfx_wood_creak_1`, `sfx_body_drag`, `sfx_pickup_key`, `sfx_pickup_item`, `sfx_paper_note`, `sfx_flashlight_click`, `sfx_wire_cut`)
   - Fonte: Kenney (`kenney.nl`) via OpenGameArt (`https://opengameart.org/content/50-rpg-sound-effects`)
   - Licença: **CC0 1.0 (Domínio Público)**

4. **Door Lock Sounds** (`sfx_door_open`, `sfx_doorknob_rattle`, `sfx_lock_rattle`, `sfx_unlock_door`)
   - Fonte: `davesch` via OpenGameArt (`https://opengameart.org/content/door-lock-sounds`)
   - Licença: **CC0 1.0 (Domínio Público)**

5. **80 CC0 RPG SFX** (`sfx_distant_metal`)
   - Fonte: `NoirPhantom89` via OpenGameArt (`https://opengameart.org/content/80-cc0-rpg-sfx`)
   - Licença: **CC0 1.0 (Domínio Público)**

6. **Metal Impact Sounds** (`sfx_body_thud`)
   - Fonte: `Hazmat Harry` via OpenGameArt (`https://opengameart.org/content/metal-impact-sounds`)
   - Licença: **CC0 1.0 (Domínio Público)**

7. **Ambient Horror / Crickets / Heartbeat / Wind / Tree Creak**
   - `amb_house_horror`: `Pixels67` (`https://opengameart.org/content/ambient-horror`) — **CC0 1.0**
   - `amb_night_crickets`: `2DPIXX` (`https://opengameart.org/content/crickets-ambient-noise-loopable`) — **CC0 1.0**
   - `amb_wind_loop`: OpenGameArt (`https://opengameart.org/content/wind-whoosh-loop`) — **CC0 1.0**
   - `sfx_heartbeat_real`: `estevinho` (`https://opengameart.org/content/heartbeat-sounds`) — **CC0 1.0**
   - `sfx_wood_creak_2`: OpenGameArt (`https://opengameart.org/content/tree-creaking`) — **CC0 1.0**

8. **Wikimedia Commons** (`sfx_door_creak_1`, `sfx_door_creak_2`, `sfx_window_wind`)
   - `LL-Q1860 (eng)-Knites-Sound recording. Door creaks.wav` — **CC0 1.0**
   - `Door handle creaking.ogg` — **Domínio Público**
   - `Howling wind.ogg` — **CC0 1.0**

## Dublagem em Português Brasileiro (Vovó e Protagonista)
- **Vovó (`voice_vovo_*.ogg`, 14 falas):** modelo neural nativo `pt-BR` **`dii-high`** (`cristianoaredes/piper-pt-br`, licença **MIT**, VITS + `espeak-ng` `pt-br`), afinado por reamostragem linear sem artefatos (`soxr`, sem `atempo`) e equalização de tórax/rouquidão, combinado com inspirações sutis de áudio humano em domínio público.
- **Protagonista (`voice_jovem_*.ogg`, 12 falas):** modelo neural nativo `pt-BR` **`pt_BR-cadu-medium`** (`rhasspy/piper-voices` / `csukuangfj/vits-piper-pt_BR-cadu-medium`, licença **MIT** / dataset **CC0 1.0**, VITS + `espeak-ng` `pt-br`), montado frase a frase com pausas dramáticas, suspiros e ofegos humanos.
- **Respirações humanas integradas às falas:**
  - `Four_sighs.ogg` e `Heavy_breathing_3.ogg` — Wikimedia Commons, licença **Domínio Público (Public Domain)**.
  - Trecho curto de ofego derivado de `sfx_scared_breath_1.ogg` (Little Robot Sound Factory) — licença **CC-BY 3.0**.
