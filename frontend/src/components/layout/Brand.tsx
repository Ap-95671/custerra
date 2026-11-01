import Image from 'next/image';

export default function Brand() {
  return <a href="/" className="brand" aria-label="Custerra home">
    <Image src="/brand/custerra-logo.jpg" alt="" width={56} height={56} priority />
    <span>Custerra</span><span className="brand-note">HAZARD INTELLIGENCE</span>
  </a>;
}
