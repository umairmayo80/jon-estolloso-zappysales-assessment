# Submission source

1. Replace `LastName`, `FirstName`, and the date in both the filename and YAML
   metadata of the Markdown source.
2. Generate the selected mockup previews with
   `scripts/render-mockup-previews.sh`. The PDF source embeds the generated
   Directory Desk desktop overview from `design/previews/`; it shows login,
   directory, and profile/address record states together.
3. Run `scripts/render-submission-pdf.sh <renamed-source.md>`.
4. Open the generated page PNGs under `output/pdf/previews/` and visually check
   page breaks, tables, code diagrams, headers, footers, and page numbers.
5. Submit the generated `output/pdf/<same-basename>.pdf`, not this Markdown
   source. Do not include `.env`, keys, or credentials in the ZIP/PDF.

The supplied source deliberately uses Markdown tables/code diagrams, so it
remains PDF-ready without an external diagram renderer. Do not include a mock
screenshot containing live PII or credentials.

`pandoc-header.tex` adds stable header/footer text and page numbers to the PDF;
keep it adjacent to the Markdown source when renaming the submission file.

The mockup helper runs headless Chrome with `--no-sandbox` in an isolated
temporary profile because some CI/container environments cannot provide Chrome's
normal sandbox. It is only appropriate for these trusted local HTML mockups.
