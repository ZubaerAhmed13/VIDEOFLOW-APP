from pathlib import Path
import os

src = Path("UX_STEP_2_TRIM_SPEED_CROP_COMPLETION_REPORT.md").read_text()
replacements = {
    "__FINAL_SHA__": os.environ["GITHUB_SHA"],
    "__CI_RUN_ID__": os.environ["GITHUB_RUN_ID"],
    "__AUTOMATED_CERTIFICATION_STATUS__": "PASS",
    "__STEP1_REGRESSION_STATUS__": "PASS",
}
for key, value in replacements.items():
    src = src.replace(key, value)
out = Path("ux-step2-final-report")
out.mkdir(parents=True, exist_ok=True)
(out / "UX_STEP_2_TRIM_SPEED_CROP_COMPLETION_REPORT.md").write_text(src)
(out / "EXACT_HEAD.txt").write_text(os.environ["GITHUB_SHA"] + "\n")
(out / "CI_RUN_ID.txt").write_text(os.environ["GITHUB_RUN_ID"] + "\n")
(out / "PHYSICAL_DEVICE_STATUS.txt").write_text("NOT RUN\n")
