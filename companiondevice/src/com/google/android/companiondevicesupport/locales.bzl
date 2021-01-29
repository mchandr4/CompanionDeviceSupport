"""Locales constants."""

# List of locales for Translation Console integration.
# The format is according to the TC dump rules, that will be converted to
# folder names in Android format, for example: "en-AU" -> "en-rAU"
TC_LOCALES = [
    "af",  # Afrikaans
    "am",  # Amharic
    "ar",  # Arabic
    "as",  # Assamese
    "az",  # Azerbaijani
    "be",  # Belarusian
    "bg",  # Bulgarian
    "bn",  # Bengali
    "bs",  # Bosnian
    "ca",  # Catalan
    "cs",  # Czech
    "da",  # Danish
    "de",  # German
    "el",  # Greek
    "en-AU",  # English (Australia)
    "en-CA",
    "en-GB",  # English (United Kingdom)
    "en-IN",  # English (India)
    "en-XC",
    "es",  # Spanish (Spain)
    "es-US",  # Spanish (United States)
    "et",  # Estonian
    "eu",  # Basque
    "fa",  # Farsi
    "fi",  # Finnish
    "fr",  # French
    "fr-CA",  # French (Canada)
    "gl",  # Galician
    "gu",  # Gujarati
    "hi",  # Hindi
    "hr",  # Croatian
    "hu",  # hungarian
    "hy",  # Armenian
    "in",  # Indonesian
    "is",  # Icelandic
    "it",  # Italian
    "iw",  # Hebrew
    "ja",  # Japanese
    "ka",  # Georgian
    "kk",  # Kazakh
    "km",  # Khmer
    "kn",  # Kannada
    "ko",  # Korean
    "ky",  # Kyrgyz
    "lo",  # Lao
    "lt",  # Lithuanian
    "lv",  # Latvian
    "mk",  # Macedonian
    "ml",  # Malayalam
    "mn",  # Mongolian
    "mr",  # Marathi
    "ms",  # Malay
    "my",  # Burmese / Myanmar
    "nb",  # Norwegian
    "ne",  # Nepali
    "nl",  # Dutch
    "or",  # Odia
    "pa",  # Punjabi
    "pl",  # Polish
    "pt",
    "pt-PT",  # Portuguese (Portugal)
    "ro",  # Romanian
    "ru",  # Russian
    "si",  # Sinhala
    "sk",  # Slovak
    "sl",  # Slovenian
    "sq",  # Albanian
    "sr",  # Serbian (Cyrillic)
    "sr-Latn",  # Serbian (Latin)
    "sv",  # Swedish
    "sw",  # Swahili
    "ta",  # Tamil
    "te",  # Telugu
    "th",  # Thai
    "tl",  # Filipino
    "tr",  # Turkish
    "uk",  # Ukranian
    "ur",  # Urdu
    "uz",  # Uzbek
    "vi",  # Vietnamese
    "zh-CN",  # Chinese (simplified)
    "zh-HK",  # Chinese (Traditional)
    "zh-TW",  # Chinese (Taiwan)
    "zu",  # Zulu
]

def get_tc_locales():
    """Returns the list of locales used by the Translation Console. See go/androidlanguages.

    Returns:
      (list) List of locales
    """
    return TC_LOCALES
