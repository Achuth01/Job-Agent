package com.example.demo.tools;

import com.example.demo.models.JobListing;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class JobSearchTool {

    private static final int MAX_RESULTS_PER_SOURCE = 20;
    private static final int PAGE_TIMEOUT_MS = 20000;
    private static final Logger log = LoggerFactory.getLogger(JobSearchTool.class);

    public JobSearchTool() {
    }

    @Tool(description = "Search for job listings by skill keywords and location. Returns list of matching jobs.")
    public List<JobListing> searchJobs(
            @ToolParam(description = "Skills or job title e.g. 'Java Spring AI backend'") String keywords,
            @ToolParam(description = "Location e.g. 'Hyderabad' or 'remote'") String location) {

        log.info("job search start: keywords={} location={}", safeString(keywords), safeString(location));
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("en-US")
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36"));

            List<JobListing> results = new ArrayList<>();
            results.addAll(fetchLinkedIn(context, keywords, location));
            results.addAll(fetchInstahyre(context, keywords, location));
            results.addAll(fetchGoogleJobs(context, keywords, location));
            context.close();
            browser.close();
            log.info("job search completed: totalResults={}", results.size());
            return results;
        } catch (Exception ignored) {
            log.error("job search failed", ignored);
            return List.of();
        }
    }

    private List<JobListing> fetchLinkedIn(BrowserContext context, String keywords, String location) {
        List<JobListing> results = new ArrayList<>();
        String uri = "https://www.linkedin.com/jobs/search/?keywords=" + encode(keywords)
                + "&location=" + encode(location);
        Page page = newPage(context);
        try {
            log.debug("linkedin search navigate: {}", uri);
            page.navigate(uri);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(1000);

            Locator cards = page.locator("ul.jobs-search__results-list li");
            if (cards.count() == 0) {
                cards = page.locator("li.jobs-search-results__list-item");
            }

            int total = Math.min((int) cards.count(), MAX_RESULTS_PER_SOURCE);
            for (int i = 0; i < total; i++) {
                Locator card = cards.nth(i);
                String title = safeText(card.locator("h3"));
                String company = safeText(card.locator("h4"));
                if (company.isBlank()) {
                    company = safeText(card.locator(".base-search-card__subtitle"));
                }
                String jobLocation = safeText(card.locator(".job-search-card__location"));
                String datePosted = safeText(card.locator("time"));
                String url = safeAttr(card.locator("a.base-card__full-link"), "href");
                if (url.isBlank()) {
                    url = safeAttr(card.locator("a"), "href");
                }

                if (!matchesLocation(location, jobLocation)) {
                    continue;
                }
                if (!matchesKeywords(title + " " + company + " " + jobLocation, keywords)) {
                    continue;
                }

                results.add(new JobListing(
                        buildId("linkedin", url, title, company),
                        title,
                        company,
                        jobLocation,
                        "",
                        datePosted,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "linkedin",
                        cleanUrl(url)
                ));
            }
        } catch (Exception ignored) {
            log.warn("linkedin search failed", ignored);
        } finally {
            page.close();
        }

        log.debug("linkedin search results: {}", results.size());
        return results;
    }

    private List<JobListing> fetchInstahyre(BrowserContext context, String keywords, String location) {
        List<JobListing> results = new ArrayList<>();
        String uri = "https://www.instahyre.com/jobs/?keyword=" + encode(keywords)
                + "&location=" + encode(location);
        Page page = newPage(context);
        try {
            log.debug("instahyre search navigate: {}", uri);
            page.navigate(uri);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(1000);

            Locator cards = page.locator(".job-card, .job-listing, .job-card-item");
            int total = Math.min((int) cards.count(), MAX_RESULTS_PER_SOURCE);

            for (int i = 0; i < total; i++) {
                Locator card = cards.nth(i);
                String title = safeText(card.locator(".job-title"));
                if (title.isBlank()) {
                    title = safeText(card.locator("h3"));
                }
                String company = safeText(card.locator(".company-name"));
                if (company.isBlank()) {
                    company = safeText(card.locator(".job-company"));
                }
                String jobLocation = safeText(card.locator(".job-location"));
                String jobType = safeText(card.locator(".job-type"));
                String url = safeAttr(card.locator("a"), "href");

                if (!matchesLocation(location, jobLocation)) {
                    continue;
                }
                if (!matchesKeywords(title + " " + company + " " + jobLocation, keywords)) {
                    continue;
                }

                results.add(new JobListing(
                        buildId("instahyre", url, title, company),
                        title,
                        company,
                        jobLocation,
                        jobType,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "instahyre",
                        cleanUrl(url)
                ));
            }
        } catch (Exception ignored) {
            log.warn("instahyre search failed", ignored);
        } finally {
            page.close();
        }

        log.debug("instahyre search results: {}", results.size());
        return results;
    }

    private List<JobListing> fetchGoogleJobs(BrowserContext context, String keywords, String location) {
        List<JobListing> results = new ArrayList<>();
        String query = String.format("%s %s jobs", safeString(keywords), safeString(location)).trim();
        String uri = "https://www.google.com/search?q=" + encode(query) + "&ibp=htl;jobs&hl=en&gl=us";

        Page page = newPage(context);
        try {
            log.debug("google jobs search navigate: {}", uri);
            page.navigate(uri);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            dismissGoogleConsent(page);

            Locator cards = page.locator("div[role='listitem']");
            int total = Math.min((int) cards.count(), MAX_RESULTS_PER_SOURCE);

            for (int i = 0; i < total; i++) {
                Locator card = cards.nth(i);
                String title = safeText(card.locator("div[role='heading']"));
                if (title.isBlank()) {
                    title = safeText(card.locator("h3"));
                }
                String company = safeText(card.locator("div[class*='company']"));
                String jobLocation = safeText(card.locator("div[class*='location']"));
                String datePosted = safeText(card.locator("span[class*='date']"));
                String jobType = safeText(card.locator("span[class*='employment']"));

                String description = "";
                String url = "";
                try {
                    card.click(new Locator.ClickOptions().setTimeout(PAGE_TIMEOUT_MS));
                    page.waitForTimeout(500);
                    description = safeText(page.locator("div[jsname='HBMdr']"));
                    if (description.isBlank()) {
                        description = safeText(page.locator("div[jsname='jDtH8b']"));
                    }
                    Locator applyLink = page.locator("a:has-text(\"Apply\")");
                    url = safeAttr(applyLink, "href");
                } catch (Exception ignored) {
                }

                if (!matchesLocation(location, jobLocation)) {
                    continue;
                }
                if (!matchesKeywords(title + " " + company + " " + jobLocation + " " + description, keywords)) {
                    continue;
                }

                results.add(new JobListing(
                        buildId("google_jobs", url, title, company),
                        title,
                        company,
                        jobLocation,
                        jobType,
                        datePosted,
                        "",
                        "",
                        "",
                        "",
                        description,
                        "google_jobs",
                        cleanUrl(url)
                ));
            }
        } catch (Exception ignored) {
            log.warn("google jobs search failed", ignored);
        } finally {
            page.close();
        }

        log.debug("google jobs search results: {}", results.size());
        return results;
    }

    private boolean matchesKeywords(String text, String keywords) {
        String kw = normalize(keywords);
        if (kw.isEmpty()) {
            return true;
        }

        String haystack = normalize(text);
        for (String token : kw.split(" ")) {
            if (!token.isBlank() && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesLocation(String requested, String jobLocation) {
        String req = normalize(requested);
        if (req.isEmpty()) {
            return true;
        }
        if (req.contains("remote")) {
            return normalize(jobLocation).contains("remote");
        }
        return normalize(jobLocation).contains(req);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private Page newPage(BrowserContext context) {
        Page page = context.newPage();
        page.setDefaultTimeout(PAGE_TIMEOUT_MS);
        return page;
    }

    private String safeText(Locator locator) {
        try {
            if (locator == null || locator.count() == 0) {
                return "";
            }
            String value = locator.first().innerText();
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeAttr(Locator locator, String name) {
        try {
            if (locator == null || locator.count() == 0) {
                return "";
            }
            String value = locator.first().getAttribute(name);
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String cleanUrl(String url) {
        if (url == null) {
            return "";
        }
        int hash = url.indexOf('#');
        int q = url.indexOf('?');
        int cut = -1;
        if (hash > -1 && q > -1) {
            cut = Math.min(hash, q);
        } else if (hash > -1) {
            cut = hash;
        } else if (q > -1) {
            cut = q;
        }
        return cut > -1 ? url.substring(0, cut) : url;
    }

    private String encode(String value) {
        return URLEncoder.encode(safeString(value), StandardCharsets.UTF_8);
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildId(String source, String url, String title, String company) {
        String seed = source + "|" + safeString(url) + "|" + safeString(title) + "|" + safeString(company);
        return Integer.toHexString(Objects.hash(seed));
    }

    private void dismissGoogleConsent(Page page) {
        try {
            Locator consent = page.locator("button:has-text(\"I agree\"), button:has-text(\"Accept all\"), button:has-text(\"Accept\")");
            if (consent.count() > 0) {
                consent.first().click(new Locator.ClickOptions().setTimeout(2000));
                page.waitForTimeout(500);
            }
        } catch (Exception ignored) {
        }
    }
}
