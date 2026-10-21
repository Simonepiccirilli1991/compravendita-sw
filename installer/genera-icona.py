"""Disegna l'icona del programma (una tazza fumante) in PNG e ICO.

Uso: python installer/genera-icona.py   (serve Pillow: pip install pillow)
Scrive src/main/resources/desktop/icona.png e installer/icona.ico.
"""
from pathlib import Path

from PIL import Image, ImageDraw

RADICE = Path(__file__).resolve().parent.parent
LATO = 1024  # si disegna grande e poi si riduce, per bordi morbidi
SFONDO = (15, 118, 110, 255)  # verde acqua
BIANCO = (255, 255, 255, 255)


def disegna() -> Image.Image:
    img = Image.new("RGBA", (LATO, LATO), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((40, 40, LATO - 40, LATO - 40), radius=200, fill=SFONDO)
    # manico: anello a destra del corpo
    d.ellipse((560, 460, 840, 740), outline=BIANCO, width=70)
    # corpo della tazza
    d.rounded_rectangle((220, 400, 660, 840), radius=70, fill=BIANCO)
    # piattino
    d.rounded_rectangle((170, 840, 770, 900), radius=30, fill=BIANCO)
    # vapore: tre linee ondulate
    for x in (330, 440, 550):
        punti = [(x + (30 if i % 2 else -30) * (1 if i % 4 < 2 else -1) * 0.8, 340 - i * 40) for i in range(6)]
        d.line(punti, fill=BIANCO, width=34, joint="curve")
    return img


def main() -> None:
    img = disegna()
    png = RADICE / "src/main/resources/desktop/icona.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    img.resize((256, 256), Image.LANCZOS).save(png)
    img.resize((256, 256), Image.LANCZOS).save(
        RADICE / "installer/icona.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("icone create")


if __name__ == "__main__":
    main()
