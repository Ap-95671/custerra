import type { History } from '../../types/history';
export default function HistoryCharts({ data }: { data: History }) {
  if (!data.events.length) return <p>No returned observations to chart. An empty catalog response does not establish safety.</p>;
  const magnitude = [0,0,0,0,0]; let missing=0;
  const months = new Map<string,number>();
  for (const event of data.events) {
    const value=event.measurements.magnitude;
    if (value === undefined || !Number.isFinite(value)) missing++;
    else magnitude[Math.max(0,Math.min(4,Math.floor(value)-3))]++;
    const month=event.startedAt.slice(0,7);months.set(month,(months.get(month)??0)+1);
  }
  const distribution=magnitude.map((count,index)=>({label:['<4','4–<5','5–<6','6–<7','≥7'][index],count}));
  const temporal=[...months.entries()].sort(([a],[b])=>a.localeCompare(b)).map(([label,count])=>({label,count}));
  // Bound the visual while retaining the entire table/evidence below. No omitted month becomes a zero.
  const recent=temporal.slice(-6);
  return <><div className="history-charts">{[{title:'Magnitude distribution',values:distribution},{title:'Returned records by month · latest 6 nonempty months',values:recent}].map(chart=><figure key={chart.title}><figcaption>{chart.title}</figcaption><div className="bar-chart" role="img" aria-label={chart.values.map(v=>`${v.label}: ${v.count}`).join('; ')}>{chart.values.map(value=><div key={value.label} className="bar-column" title={`${value.label}: ${value.count} records`}><strong>{value.count}</strong><span style={{height:`${value.count/Math.max(...chart.values.map(v=>v.count),1)*80}%`}}/><small>{value.label}</small></div>)}</div></figure>)}</div><p className="timestamp">Charts describe returned records only.{data.truncated?' Catalog response is incomplete; these counts are lower bounds.':''} Missing months are not asserted to be event-free. {missing>0?`${missing} records have no usable magnitude.`:''} Historical frequency is not future probability.</p></>;
}
