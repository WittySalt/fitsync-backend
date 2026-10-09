"""Unit tests for the header rewrite. Run: python -m unittest discover -s <this dir>"""

import unittest
from email import policy
from email.parser import BytesParser

from forwarder import rewrite

RAW = (
    b"Return-Path: <bounce@mail.example.org>\r\n"
    b"DKIM-Signature: v=1; a=rsa-sha256; d=example.org; s=s1; b=abc\r\n"
    b"Sender: list@example.org\r\n"
    b"From: Alice Tan <alice@example.org>\r\n"
    b"To: privacy@fitsyncapp.click\r\n"
    b"Subject: Delete my data\r\n"
    b"Content-Type: text/plain; charset=utf-8\r\n"
    b"\r\n"
    b"Please delete my account.\r\n"
)


def parse(raw: bytes):
    return BytesParser(policy=policy.default).parsebytes(raw)


class RewriteTest(unittest.TestCase):
    def setUp(self):
        self.out = parse(rewrite(RAW, "noreply@fitsyncapp.click", "FitSync Privacy"))

    def test_from_is_our_sender(self):
        self.assertEqual(self.out["From"], "FitSync Privacy <noreply@fitsyncapp.click>")
        self.assertEqual(len(self.out.get_all("From")), 1)

    def test_reply_to_is_original_sender(self):
        self.assertEqual(self.out["Reply-To"], "Alice Tan <alice@example.org>")

    def test_subject_is_prefixed(self):
        self.assertEqual(self.out["Subject"], "[privacy@] Delete my data")

    def test_signature_and_envelope_headers_are_stripped(self):
        for name in ("DKIM-Signature", "Return-Path", "Sender"):
            self.assertIsNone(self.out[name], name)

    def test_body_and_recipient_are_kept(self):
        self.assertEqual(self.out["To"], "privacy@fitsyncapp.click")
        self.assertIn("Please delete my account.", self.out.get_content())

    def test_existing_reply_to_wins_over_from(self):
        raw = RAW.replace(b"Subject:", b"Reply-To: help@example.org\r\nSubject:")
        out = parse(rewrite(raw, "noreply@fitsyncapp.click", "FitSync Privacy"))
        self.assertEqual(out["Reply-To"], "help@example.org")

    def test_prefix_is_not_doubled(self):
        raw = RAW.replace(b"Subject: Delete", b"Subject: [privacy@] Delete")
        out = parse(rewrite(raw, "noreply@fitsyncapp.click", "FitSync Privacy"))
        self.assertEqual(out["Subject"], "[privacy@] Delete my data")

    def test_non_ascii_subject_survives(self):
        raw = RAW.replace(b"Subject: Delete my data", b"Subject: =?utf-8?b?5Yig6Zmk?=")
        out = parse(rewrite(raw, "noreply@fitsyncapp.click", "FitSync Privacy"))
        self.assertEqual(out["Subject"], "[privacy@] 删除")

    def test_missing_subject(self):
        raw = RAW.replace(b"Subject: Delete my data\r\n", b"")
        out = parse(rewrite(raw, "noreply@fitsyncapp.click", "FitSync Privacy"))
        self.assertEqual(out["Subject"], "[privacy@] ")


if __name__ == "__main__":
    unittest.main()
