import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import store_graphics


class StoreGraphicsTest(unittest.TestCase):
    def test_every_screenshot_has_a_caption_in_each_language(self):
        english, german = store_graphics.CAPTIONS["en"], store_graphics.CAPTIONS["de"]

        self.assertEqual(set(english), set(german))

    def test_mo_is_cut_from_the_launcher_icon(self):
        figure = store_graphics.mo("10 10 88 88")

        self.assertIn('viewBox="10 10 88 88"', figure)
        self.assertIn(store_graphics.website.app_colors()["mo_icon_belly"], figure)


if __name__ == "__main__":
    unittest.main()
