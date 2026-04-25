# ktlint Code Style

This project uses [ktlint](https://pinterest.github.io/ktlint/) for Kotlin code style enforcement and formatting.

## Configuration

- **Version:** 1.4.1
- **Config File:** `.editorconfig`
- **Line Length:** 120 characters
- **Indent:** 4 spaces
- **Tabs/Spaces:** Spaces

## Usage

### Check Code Style

```bash
./gradlew ktlintCheck
```

This command will print the ktlint JAR path and the command needed to run the check.

### Format Code

```bash
./gradlew ktlintFormat
```

This command will print the ktlint JAR path and the command needed to format the code.

### Manual Execution

If you have Java installed locally, you can run ktlint directly:

```bash
# Check
java -jar ~/.gradle/caches/modules-2/files-2.1/com.pinterest.ktlint/ktlint-cli/1.4.1/*/ktlint-cli-1.4.1-all.jar --reporter=plain src/**/*.kt

# Format
java -jar ~/.gradle/caches/modules-2/files-2.1/com.pinterest.ktlint/ktlint-cli/1.4.1/*/ktlint-cli-1.4.1-all.jar -F src/**/*.kt
```

### IDE Integration

For IntelliJ IDEA/GoLand:
1. Install the **ktlint** plugin from the marketplace
2. Go to **Settings → Tools → ktlint**
3. The `.editorconfig` file will be automatically detected

## Gradle Tasks

| Task | Description | Group |
|------|-------------|-------|
| `ktlintCheck` | Verify Kotlin code style | verification |
| `ktlintFormat` | Format Kotlin code | formatting |

## Editor Config

The `.editorconfig` file at the root of the project defines all formatting rules:
- Max line length: 120
- Indent size: 4 spaces
- Trailing commas allowed (Kotlin 1.4+)
- Final newline required

## CI/CD

To integrate ktlint into CI/CD pipelines:

```bash
./gradlew ktlintCheck  # Verify style compliance
```

This will output the command needed to run ktlint with Java available in your environment.
