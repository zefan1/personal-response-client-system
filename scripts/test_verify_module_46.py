import unittest

from scripts.verify_module_46 import tag_repository_legacy_errors


class VerifyModule46Test(unittest.TestCase):

    def test_rejects_each_legacy_tag_repository_pattern(self):
        sources = {
            "usageCount": "public int usageCount(long id) { return 0; }",
            "legacyCustomerIds": "return legacyCustomerIds(value, category);",
            "legacyColumn": "String column = legacyColumn(boundField);",
            "FROM customers": "SELECT id FrOm\n  customers WHERE id = ?",
            "LIKE CONCAT": "column LiKe  CoNcAt( '%', ?, '%' )",
        }

        for expected, source in sources.items():
            with self.subTest(expected=expected):
                self.assertTrue(
                    tag_repository_legacy_errors(source),
                    f"expected injected {expected} pattern to be rejected",
                )

    def test_allows_management_search_like(self):
        source = "c.category_name LIKE ? OR v.display_name LIKE ?"

        self.assertEqual([], tag_repository_legacy_errors(source))


if __name__ == "__main__":
    unittest.main()
