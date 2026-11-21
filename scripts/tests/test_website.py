import sys
import tempfile
import unittest
import xml.etree.ElementTree as ElementTree
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import website


class WebsiteTest(unittest.TestCase):
    def test_the_parts_of_markdown_the_texts_use_become_html(self):
        title, blocks = website.body(
            "# The title\n\nFirst line.\nSecond line.\n\n## A heading\n\n- one\n- **two** in `a.json`\n\nAfter."
        )

        self.assertEqual("The title", title)
        self.assertEqual(
            "<p>First line.<br>\nSecond line.</p>\n"
            "<h2>A heading</h2>\n"
            "<ul>\n<li>one</li>\n<li><strong>two</strong> in <code>a.json</code></li>\n</ul>\n"
            "<p>After.</p>",
            blocks,
        )

    def test_text_cannot_become_markup(self):
        self.assertEqual("a &lt;b&gt; &amp; c", website.inline("a <b> & c"))

    def test_a_contact_address_can_be_tapped(self):
        self.assertEqual(
            'Contact: <a href="mailto:app@example.org">app@example.org</a>',
            website.inline("Contact: app@example.org"),
        )

    def test_a_blank_still_to_fill_in_is_found(self):
        self.assertEqual(["<contact address>"], website.blanks("Contact: `<contact address>`\n<B> stays."))

    def test_every_text_is_built_in_both_languages_and_the_pages_link_to_each_other(self):
        with tempfile.TemporaryDirectory() as directory:
            website.build(directory)
            built = {
                (text, language): (Path(directory) / path / "index.html").read_text(encoding="utf-8")
                for text, (_, paths) in website.TEXTS.items()
                for language, path in paths.items()
            }
            icon = (Path(directory) / "icon.svg").read_text(encoding="utf-8")

        home = built["home", "en"]
        self.assertIn('<html lang="en">', home)
        self.assertIn("<h1>Momentum</h1>", home)
        self.assertIn('<a href="privacy/" lang="en" hreflang="en">Privacy policy for Momentum</a>', home)
        self.assertIn('<a href="legal-notice/" lang="en" hreflang="en">Legal notice</a>', home)
        self.assertIn('<a href="de/" lang="de" hreflang="de">Deutsch</a>', home)
        imprint = built["legal-notice", "de"]
        self.assertIn('<html lang="de">', imprint)
        self.assertIn("<h1>Impressum</h1>", imprint)
        self.assertIn('<a href="../" lang="de" hreflang="de">Momentum</a>', imprint)
        self.assertIn('<a href="../datenschutz/" lang="de" hreflang="de">Datenschutzerklärung für Momentum</a>', imprint)
        self.assertIn('<a href="../../legal-notice/" lang="en" hreflang="en">English</a>', imprint)
        self.assertIn('<link rel="icon" href="../../icon.svg">', imprint)
        # Every block of the source reaches the page: nothing the app shows is missing here.
        for (text, language), html in built.items():
            source = website.source(text, language)
            self.assertEqual(source.count("\n## "), html.count("<h2>"))
            self.assertEqual(sum(line.startswith("- ") for line in source.splitlines()), html.count("<li>"))
        # Mo is on the front page only, and he is the app's own icon.
        self.assertIn(icon, home)
        self.assertNotIn("<svg", imprint)

    def test_an_address_keeps_its_lines(self):
        _, blocks = website.body(website.source("legal-notice", "de"))

        self.assertIn("<p>Nicolas Mahn<br>\n", blocks)

    def test_a_text_left_out_is_neither_built_nor_linked(self):
        with tempfile.TemporaryDirectory() as directory:
            website.build(directory, ["home", "privacy-policy"])

            self.assertFalse((Path(directory) / "legal-notice").exists())
            self.assertNotIn("legal-notice", (Path(directory) / "index.html").read_text(encoding="utf-8"))

    def test_the_icon_is_drawn_from_every_path_of_the_launcher_icon(self):
        icon = ElementTree.fromstring(website.icon_svg())
        launcher = ElementTree.parse(website.RESOURCES / "drawable/ic_launcher_foreground.xml").getroot()

        svg = "{http://www.w3.org/2000/svg}"
        drawn = [path for path in icon.iter(f"{svg}path")]
        self.assertEqual(len(list(launcher.iter("path"))) + len(list(launcher.iter("clip-path"))), len(drawn))
        self.assertFalse([path for path in drawn if "@color" in "".join(path.attrib.values())])


if __name__ == "__main__":
    unittest.main()
