GARMIN_REPO := git@github.com:synheart-ai/synheart-wear-garmin-companion.git
GARMIN_SUBDIR := kotlin

.PHONY: build build-with-garmin build-without-garmin check-garmin fetch-garmin link-garmin clean-garmin

# Auto-detect: build with Garmin RTS if you have access, otherwise without
build:
	@if git ls-remote $(GARMIN_REPO) HEAD >/dev/null 2>&1; then \
		echo "✓ Garmin companion repo access detected"; \
		$(MAKE) build-with-garmin; \
	else \
		echo "○ No Garmin companion access — building without RTS"; \
		$(MAKE) build-without-garmin; \
	fi

# Explicit targets
build-with-garmin: fetch-garmin link-garmin
	@echo "Building Kotlin SDK with Garmin RTS support..."

build-without-garmin: clean-garmin
	@echo "Building Kotlin SDK without Garmin RTS..."

# Check repo access
check-garmin:
	@git ls-remote $(GARMIN_REPO) HEAD >/dev/null 2>&1 \
		&& echo "✓ Access OK" \
		|| (echo "✗ No access to $(GARMIN_REPO)" && exit 1)

# Clone or pull companion repo into .garmin/
fetch-garmin: check-garmin
	@if [ ! -d ".garmin" ]; then \
		echo "Cloning companion into .garmin/ ..."; \
		git clone --depth 1 $(GARMIN_REPO) .garmin; \
	else \
		echo "Updating .garmin/ ..."; \
		git -C .garmin pull --ff-only; \
	fi

# Symlink companion GarminHealth.kt (replaces stub)
link-garmin:
	@echo "Linking Kotlin Garmin RTS files..."
	@ln -sf $$(pwd)/.garmin/$(GARMIN_SUBDIR)/src/main/kotlin/ai/synheart/wear/adapters/GarminHealth.kt \
		src/main/kotlin/ai/synheart/wear/adapters/GarminHealth.kt
	@echo "✓ Garmin RTS files linked"

# Remove symlinks and .garmin/ directory
clean-garmin:
	@rm -rf .garmin
	@# Remove symlink if dangling
	@if [ -L src/main/kotlin/ai/synheart/wear/adapters/GarminHealth.kt ]; then \
		rm src/main/kotlin/ai/synheart/wear/adapters/GarminHealth.kt; \
	fi
	@echo "✓ Garmin RTS files cleaned"
