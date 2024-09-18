import Vue from "vue";

export default class GoogleAnalytics extends Vue {
  private tag!: string;
  private script: HTMLScriptElement | undefined;
  private enabled: boolean = false;
  private isLoaded: boolean = false;

  /**
   * This is a work-around for old vue-router not having access to injected objects. By default GoogleAnalytics should be accessed with @Inject.
   */
  static instance: GoogleAnalytics | undefined;

  constructor() {
    super();
    GoogleAnalytics.instance = this;
  }

  gtag(...params: any[]) {
    if (this.enabled && window.dataLayer !== undefined) {
      console.log("GA", arguments);
      window.dataLayer.push(arguments);
    }
  }

  /**
   * @see https://developers.google.com/tag-platform/gtagjs/reference/events#page_view
   */
  page_view(page_location: string | undefined) {
    try {
      this.gtag("event", "page_view", { page_location: page_location, language: this.$i18n?.locale });
    } catch (e) {
      console.error(e);
    }
  }

  /**
   * @see https://developers.google.com/tag-platform/gtagjs/reference/events#login
   */
  login(method: string | undefined) {
    try {
      this.gtag("event", "login", { method: method });
    } catch (e) {
      console.error(e);
    }
  }

  private load() {
    if (this.isLoaded || !this.enabled || !this.hasNonEmptyTag()) return;
    if (this.script === undefined) {
      this.script = document.createElement("script");
      this.script.type = "text/javascript";
      this.script.async = true;
      document.head.appendChild(this.script);
    }
    this.script.src = `https://www.googletagmanager.com/gtag/js?id=${this.tag}`;
    window.dataLayer = window.dataLayer || [];
    this.gtag("js", new Date());
    this.gtag("config", this.tag);
    this.isLoaded = true;
  }

  setTag(tag: string) {
    if (this.tag == tag && this.tag.length > 0) return;
    this.tag = tag;
    this.load();
  }

  hasNonEmptyTag(): boolean {
    return this.tag !== undefined && this.tag.length > 0;
  }

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
    this.load();
  }
}
