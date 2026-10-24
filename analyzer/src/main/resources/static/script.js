const codeInput = document.getElementById("codeInput");
const analyzeButton = document.getElementById("analyzeButton");
const exampleButton = document.getElementById("exampleButton");

const resultsSection = document.getElementById("resultsSection");
const errorMessage = document.getElementById("errorMessage");

const exampleCode = `public class Example {

    public int factorial(int n) {
        if (n <= 1) {
            return 1;
        }

        return n * factorial(n - 1);
    }

    public void process(int[] numbers) {
        for (int i = 0; i < numbers.length; i++) {
            for (int j = 0; j < numbers.length; j++) {
                System.out.println(numbers[i] + numbers[j]);
            }
        }
    }
}`;

// Показываем пример при первом открытии страницы.
codeInput.value = exampleCode;

exampleButton.addEventListener("click", () => {
    codeInput.value = exampleCode;
    errorMessage.textContent = "";
});

analyzeButton.addEventListener("click", analyzeCode);

async function analyzeCode() {
    const code = codeInput.value.trim();

    errorMessage.textContent = "";

    if (!code) {
        resultsSection.classList.add("hidden");
        errorMessage.textContent = "Сначала вставь Java-код.";
        return;
    }

    analyzeButton.disabled = true;
    analyzeButton.textContent = "Анализируем...";

    try {
        const response = await fetch("/api/analyze", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ code: code })
        });

        const report = await response.json();

        if (!response.ok) {
            throw new Error(
                report.error || "Ошибка анализа Java-кода."
            );
        }

        displayResults(report);

    } catch (error) {
        resultsSection.classList.add("hidden");

        errorMessage.textContent =
            "Не удалось выполнить анализ: " + error.message;

    } finally {
        analyzeButton.disabled = false;
        analyzeButton.textContent = "Анализировать код →";
    }
}

function displayResults(report) {
    document.getElementById("qualityScore").textContent =
        report.qualityScore ?? "—";

    document.getElementById("methodsAnalyzed").textContent =
        report.methodsAnalyzed ?? 0;

    document.getElementById("issuesFound").textContent =
        report.issuesFound ?? 0;

    const summary = report.summary || {};

    document.getElementById("highCount").textContent =
        summary.high ?? 0;

    document.getElementById("mediumCount").textContent =
        summary.medium ?? 0;

    document.getElementById("lowCount").textContent =
        summary.low ?? 0;

    const issuesList = document.getElementById("issuesList");

    // Удаляем старые результаты.
    issuesList.replaceChildren();

    const issues = Array.isArray(report.issues)
        ? report.issues
        : [];

    if (issues.length === 0) {
        const emptyState = document.createElement("div");
        emptyState.className = "empty-state";
        emptyState.textContent =
            "Замечаний не найдено. Это не гарантирует отсутствие ошибок.";

        issuesList.appendChild(emptyState);
    } else {
        issues.forEach(issue => {
            issuesList.appendChild(createIssueCard(issue));
        });
    }

    resultsSection.classList.remove("hidden");

    resultsSection.scrollIntoView({
        behavior: "smooth",
        block: "start"
    });
}

function createIssueCard(issue) {
    const card = document.createElement("div");

    const severity = String(issue.severity || "LOW")
        .toUpperCase();

    const severityClass = {
        HIGH: "high-severity",
        MEDIUM: "medium-severity",
        LOW: "low-severity"
    }[severity] || "low-severity";

    card.className = "issue-card " + severityClass;

    const header = document.createElement("div");
    header.className = "issue-header";

    const type = document.createElement("span");
    type.className = "issue-type";
    type.textContent = issue.type || "UNKNOWN";

    const badge = document.createElement("span");
    badge.className = "severity-badge";
    badge.textContent = severity;

    header.appendChild(type);
    header.appendChild(badge);

    const meta = document.createElement("div");
    meta.className = "issue-meta";

    meta.textContent =
        "Метод: " + (issue.method ?? "unknown") +
        " | Строка: " + (issue.line ?? "—") +
        " | Сложность: " + (issue.complexity ?? "—");

    const message = document.createElement("div");
    message.className = "issue-message";
    message.textContent = issue.message || "";

    const recommendation = document.createElement("div");
    recommendation.className = "issue-recommendation";

    recommendation.textContent =
        "Рекомендация: " + (issue.recommendation || "Нет рекомендации.");

    card.appendChild(header);
    card.appendChild(meta);
    card.appendChild(message);
    card.appendChild(recommendation);

    return card;
}