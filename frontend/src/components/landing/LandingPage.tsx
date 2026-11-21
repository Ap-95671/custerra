'use client';
import { useEffect, useRef, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import Icon from '../common/Icon';
import Brand from '../layout/Brand';

const archiveDate = 'Captured 4 November 2026';
const chapters = [
  { name: 'Discover', title: 'Start with a place.', text: 'Search for a town or pick a point on the map. Set the area and time window to see the observations available for that location.', image: 'v3-explorer.jpg', alt: 'The explorer centered on a public location with coordinates, search controls and source observations.', caption: `${archiveDate} · USGS observations · © OpenStreetMap contributors`, href: '/map', link: 'Explore a location' },
  { name: 'Understand', title: 'Read the source evidence.', text: 'Open a report to see who issued it, when it was updated and how far the evidence reaches. Official warnings and curated event records appear with separate source labels.', image: 'v3-timeline.jpg', alt: 'The available-feed timeline beside the selected report and its source metadata.', caption: `${archiveDate} · NWS California and NASA EONET · © OpenStreetMap contributors`, href: '/insights', link: 'Inspect the evidence' },
  { name: 'Plan', title: 'See what’s along your route.', text: 'Choose a start and destination to retrieve a driving route. Open the reported evidence near its segments and compare alternatives when the routing provider returns them.', image: 'v3-route.jpg', alt: 'Custerra’s driving route planner displaying the Berlin–Potsdam route.', caption: `${archiveDate} · OSRM · © OpenStreetMap contributors`, href: '/routes', link: 'Plan a route' },
  { name: 'Monitor', title: 'Check a saved journey.', text: 'Activate a private journey and run an evidence check. Its last attempt, source coverage and informational notices remain with the journey. You can also save a read-only copy for offline use.', image: 'v3-monitor.jpg', alt: 'An active journey in Custerra, with its route, monitoring controls and evidence timestamps.', caption: 'Captured 18 November 2026 · Disposable account and public locations · © OpenStreetMap contributors', href: '/journeys', link: 'Open journeys' },
  { name: 'Review', title: 'Read the historical record.', text: 'Retrieve a historical USGS earthquake snapshot. See catalog frequency and the magnitude distribution for the chosen period, with its coverage limits.', image: 'v3-history.jpg', alt: 'The historical earthquake explorer displaying a USGS snapshot and magnitude distribution.', caption: 'Captured 18 November 2026 · USGS earthquake catalog', href: '/history', link: 'Explore historical evidence' },
];

const publicLinks = [
  { href: '#product', label: 'Product' },
  { href: '#how-it-works', label: 'How it works' },
  { href: '#sources', label: 'Our evidence' },
  { href: '/developer', label: 'Developers' },
];

function closeMenu(event: React.MouseEvent<HTMLAnchorElement>) {
  event.currentTarget.closest('details')?.removeAttribute('open');
}

export default function LandingPage() {
  const [chapter, setChapter] = useState(0);
  const current = chapters[chapter];
  const menu = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    const disclosure = menu.current;
    function dismiss(event: KeyboardEvent) {
      if (event.key !== 'Escape' || !disclosure?.open) return;
      disclosure.open = false;
      disclosure.querySelector('summary')?.focus();
    }
    disclosure?.addEventListener('keydown', dismiss);
    return () => disclosure?.removeEventListener('keydown', dismiss);
  }, []);
  return <>
    <header className="public-header">
      <Brand/>
      <nav className="public-links" aria-label="Public navigation">
        {publicLinks.map(link => <Link key={link.href} href={link.href}>{link.label}</Link>)}
      </nav>
      <div className="public-actions">
        <Link className="login-link" href="/login">Log in</Link>
        <Link className="button button-dark public-launch" href="/map">Launch application <Icon name="arrow" size={16}/></Link>
        <details ref={menu} className="public-mobile-nav">
          <summary aria-label="Public menu"><Icon name="menu" size={20}/></summary>
          <nav aria-label="Mobile public navigation">
            {publicLinks.map(link => <Link key={link.href} href={link.href} onClick={closeMenu}>{link.label}</Link>)}
            <Link href="/login" onClick={closeMenu}>Log in</Link>
            <Link href="/map" onClick={closeMenu}>Launch application</Link>
          </nav>
        </details>
      </div>
    </header>
    <main className="landing-main" id="main-content" tabIndex={-1}>
      <section className="landing-hero">
        <h1>Understand hazards.<br/><em>Navigate uncertainty.</em></h1>
        <p>Explore reported natural hazards around a place, see the evidence along a driving route, and check what changes on a saved journey.</p>
        <div className="hero-actions">
          <Link href="/map" className="button button-dark">Open hazard map <Icon name="arrow" size={17}/></Link>
          <a href="#how-it-works" className="button hero-secondary">See how it works</a>
        </div>
      </section>
      <section id="product" className="product-introduction" aria-label="Custerra product preview">
        <figure className="product-preview">
          <Link href="/map" className="hero-product-capture" aria-label="Open the hazard map shown in this preview">
            <Image src="/product/v3-explorer.jpg" width={1440} height={1000} alt="Custerra’s explorer showing earthquake observations and original source evidence near San Francisco." priority unoptimized/>
          </Link>
          <figcaption className="image-provenance">San Francisco · {archiveDate} · USGS earthquake observations · © OpenStreetMap contributors</figcaption>
        </figure>
      </section>
      <section className="source-strip" aria-label="Implemented sources">
        <p>Evidence sources</p>
        <div><span>USGS</span><span>NASA FIRMS <small>Thermal detections</small></span><span>GDACS</span><span>NWS <small>California only</small></span><span>NASA EONET <small>Curated context</small></span><span>Open-Meteo <small>Modeled context</small></span></div>
      </section>
      <section id="how-it-works" className="workflow-story">
        <div className="section-intro"><h2>From a point on the map<br/>to a journey you can check.</h2></div>
        <div className="workflow-layout">
          <div className="chapter-list" role="tablist" aria-label="Product workflow">
            {chapters.map((item, index) => <button key={item.name} id={`chapter-${index}`} role="tab"
              aria-selected={chapter === index} aria-controls="chapter-panel" tabIndex={chapter === index ? 0 : -1}
              className={chapter === index ? 'active' : ''} onClick={() => setChapter(index)}
              onKeyDown={event => {
                const deltas: Record<string, number> = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 };
                const delta = deltas[event.key];
                if (delta === undefined && event.key !== 'Home' && event.key !== 'End') return;
                event.preventDefault();
                const last = chapters.length - 1;
                const next = event.key === 'Home' ? 0 : event.key === 'End' ? last : (index + delta + chapters.length) % chapters.length;
                setChapter(next);
                document.getElementById(`chapter-${next}`)?.focus();
              }}>
              <span>{item.name}<small>{item.title}</small></span><Icon name="arrow" size={18}/>
            </button>)}
          </div>
          <div id="chapter-panel" className="chapter-panel" role="tabpanel" aria-labelledby={`chapter-${chapter}`}>
            <div className="chapter-content" key={current.name}>
              <div className="chapter-copy"><h3>{current.title}</h3><p>{current.text}</p><Link href={current.href} className="text-link">{current.link}<Icon name="arrow" size={17}/></Link></div>
              <figure className="chapter-figure">
                <div className="chapter-capture"><Image src={`/product/${current.image}`} width={1440} height={1000} unoptimized alt={current.alt}/></div>
                <figcaption className="caption">{current.caption}</figcaption>
              </figure>
            </div>
          </div>
        </div>
      </section>
      <section id="sources" className="evidence-story"><div className="evidence-heading"><span className="eyebrow">Clarity includes the unknowns</span><h2>Know what you’re<br/><em>looking at.</em></h2><p>A marker is a starting point. Custerra keeps the source, timestamp and coverage beside the evidence so you can understand what it says.</p></div><dl className="evidence-taxonomy"><div><dt><span className="semantic-mark observed"/>Observations</dt><dd>Reported earthquakes and satellite thermal detections. A thermal anomaly is not necessarily a confirmed wildfire.</dd></div><div><dt><span className="semantic-mark advisory"/>Advisories and curated reports</dt><dd>Selected GDACS reports and NASA EONET records retain their source classifications and geographic limits.</dd></div><div><dt><span className="semantic-mark official"/>Official warnings</dt><dd>Active NWS products for California include source instructions and the warning area where supplied.</dd></div><div><dt><span className="semantic-mark experimental"/>Experimental indicators</dt><dd>Explainable activity and proximity indices. Overall location and route risk remain UNKNOWN.</dd></div><div><dt><span className="semantic-mark unknown"/>Unknown & unavailable</dt><dd>Missing evidence stays visible. An empty result never establishes safety. Zone-only alerts may have area text without a map boundary.</dd></div></dl></section>
      <section className="closing-section"><h2>Explore the evidence<br/><em>around a place.</em></h2><Link href="/map" className="button button-dark">Explore Custerra <Icon name="arrow" size={18}/></Link><p>Public exploration. No account required.<br/>Sign in when you want to save a journey.</p></section>
    </main><footer className="public-footer"><div><Brand/><p>Understand hazards. Navigate uncertainty.</p></div><div><Link href="/map">Explore</Link><Link href="/routes">Routes</Link><Link href="/developer">Developer API</Link><a href="https://github.com/Ap-95671/custerra#readme" target="_blank" rel="noreferrer">Documentation <Icon name="external" size={12}/></a></div><p>Experimental release candidate. Custerra is not an official emergency-warning authority. Follow local government and emergency instructions.</p><span>© {new Date().getFullYear()} Custerra</span></footer>
  </>;
}
