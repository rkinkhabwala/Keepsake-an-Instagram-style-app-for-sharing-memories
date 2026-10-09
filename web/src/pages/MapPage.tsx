import L from 'leaflet';
import 'leaflet.markercluster';
import 'leaflet/dist/leaflet.css';
import 'leaflet.markercluster/dist/MarkerCluster.css';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMapMemories } from '../api/queries';
import type { MapMemory } from '../api/types';
import { ErrorNotice } from '../components/ui';
import { formatOccurredOn } from '../lib/format';
import { viewportToBbox } from '../lib/mapBounds';

/** Builds marker contents as DOM nodes, never HTML strings, so captions and names can't inject markup. */
function thumbMarker(memory: MapMemory): HTMLElement {
  const element = document.createElement('div');
  element.className =
    'h-12 w-12 overflow-hidden rounded-lg border-2 border-white bg-stone-300 shadow-md dark:border-stone-900';
  if (memory.thumbUrl) {
    const image = document.createElement('img');
    image.src = memory.thumbUrl;
    image.alt = '';
    image.className = 'h-full w-full object-cover';
    element.append(image);
  }
  return element;
}

function clusterIcon(cluster: L.MarkerCluster): L.DivIcon {
  const markers = cluster.getAllChildMarkers();
  const element = document.createElement('div');
  element.className = 'relative h-14 w-14';
  const first = (markers[0]?.options as { memory?: MapMemory }).memory;
  if (first) element.append(thumbMarker(first));
  const count = document.createElement('span');
  count.className =
    'absolute -right-1 -top-1 rounded-full bg-emerald-700 px-1.5 text-xs font-semibold text-white shadow';
  count.textContent = String(cluster.getChildCount());
  element.append(count);
  return L.divIcon({ html: element, className: '', iconSize: [56, 56] });
}

/** Where your memories happened: your own, people you follow, and ones you're tagged in. */
export function MapPage() {
  const container = useRef<HTMLDivElement>(null);
  const clusters = useRef<L.MarkerClusterGroup | null>(null);
  const navigate = useNavigate();
  const [bbox, setBbox] = useState<string | null>(null);
  const memories = useMapMemories(bbox);

  useEffect(() => {
    if (!container.current) return;
    const map = L.map(container.current, { worldCopyJump: true }).setView([20, 0], 2);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
    const group = L.markerClusterGroup({ iconCreateFunction: clusterIcon, showCoverageOnHover: false });
    map.addLayer(group);
    clusters.current = group;
    const update = () => {
      const bounds = map.getBounds();
      setBbox(viewportToBbox(bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()));
    };
    map.on('moveend', update);
    update();
    return () => {
      map.remove();
      clusters.current = null;
    };
  }, []);

  const items = memories.data?.items;
  useEffect(() => {
    const group = clusters.current;
    if (!group || !items) return;
    group.clearLayers();
    for (const memory of items) {
      const marker = L.marker([memory.latitude, memory.longitude], {
        icon: L.divIcon({ html: thumbMarker(memory), className: '', iconSize: [48, 48] }),
        title: `${memory.locationName ?? 'Memory'}, ${formatOccurredOn(memory.occurredOn)}, by @${memory.authorUsername}`,
        keyboard: true,
        memory,
      } as L.MarkerOptions & { memory: MapMemory });
      marker.on('click', () => navigate(`/m/${memory.id}`));
      group.addLayer(marker);
    }
  }, [items, navigate]);

  return (
    <div className="flex h-[calc(100vh-7rem)] flex-col sm:h-[calc(100vh-4rem)]">
      <div className="flex items-center justify-between px-4 py-2">
        <h1 className="text-lg font-semibold">Map</h1>
        {memories.data?.truncated && (
          <p role="status" className="text-xs text-stone-500">Showing the latest 500 here. Zoom in to see more.</p>
        )}
      </div>
      <ErrorNotice error={memories.error} />
      <div ref={container} data-testid="map" className="z-0 flex-1" aria-label="Map of memories" role="region" />
    </div>
  );
}
