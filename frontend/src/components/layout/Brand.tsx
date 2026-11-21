import Image from 'next/image';
import Link from 'next/link';

export default function Brand() {
  return <Link href="/" className="brand" aria-label="Custerra home">
    <Image src="/brand/custerra-logo.jpg" alt="" width={56} height={56} priority />
    <span>Custerra<span className="brand-period">.</span></span>
  </Link>;
}
