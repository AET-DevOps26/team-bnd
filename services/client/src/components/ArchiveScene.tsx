import React from "react";

// Decorative login illustration, inline SVG (renders offline)
export default function ArchiveScene() {
  return (
    <svg
      className="archive-scene"
      viewBox="0 0 520 560"
      role="presentation"
      focusable="false"
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* Raw document stack" */}
      <g className="archive-scene__stack">
        <rect className="archive-doc archive-doc--3" x="54" y="196" width="150" height="196" rx="10" />
        <rect className="archive-doc archive-doc--2" x="42" y="176" width="150" height="196" rx="10" />
        <g className="archive-doc-top">
          <rect className="archive-doc archive-doc--1" x="30" y="156" width="150" height="196" rx="10" />
          <line className="archive-line" x1="50" y1="192" x2="160" y2="192" />
          <line className="archive-line" x1="50" y1="212" x2="160" y2="212" />
          <line className="archive-line" x1="50" y1="232" x2="132" y2="232" />
          <line className="archive-line archive-line--faint" x1="50" y1="262" x2="160" y2="262" />
          <line className="archive-line archive-line--faint" x1="50" y1="282" x2="160" y2="282" />
          <line className="archive-line archive-line--faint" x1="50" y1="302" x2="120" y2="302" />
        </g>
      </g>

      {/* Flow arrows*/}
      <g className="archive-flow">
        <path className="archive-flow__path" d="M198 200 C 250 200, 250 132, 300 132" />
        <path className="archive-flow__path archive-flow__path--2" d="M198 254 C 258 254, 258 262, 300 262" />
        <path className="archive-flow__path archive-flow__path--3" d="M198 308 C 250 308, 250 392, 300 392" />
      </g>

      {/* Distilled knowledge cards*/}
      <g className="archive-card archive-card--summary">
        <rect x="300" y="96" width="182" height="92" rx="12" />
        <text x="318" y="121" className="archive-card__tag">SUMMARY</text>
        <line className="archive-line--card" x1="318" y1="141" x2="464" y2="141" />
        <line className="archive-line--card" x1="318" y1="155" x2="464" y2="155" />
        <line className="archive-line--card" x1="318" y1="169" x2="420" y2="169" />
      </g>

      <g className="archive-card archive-card--entities">
        <rect x="300" y="216" width="182" height="92" rx="12" />
        <text x="318" y="241" className="archive-card__tag">ENTITIES</text>
        <rect className="archive-pill archive-pill--a" x="318" y="253" width="60" height="18" rx="9" />
        <rect className="archive-pill archive-pill--b" x="386" y="253" width="46" height="18" rx="9" />
        <rect className="archive-pill archive-pill--c" x="318" y="277" width="52" height="18" rx="9" />
        <rect className="archive-pill archive-pill--a" x="378" y="277" width="72" height="18" rx="9" />
      </g>

      <g className="archive-card archive-card--tags">
        <rect x="300" y="336" width="182" height="92" rx="12" />
        <text x="318" y="361" className="archive-card__tag">TAGS</text>
        <rect className="archive-pill archive-pill--c" x="318" y="373" width="54" height="18" rx="9" />
        <rect className="archive-pill archive-pill--a" x="380" y="373" width="44" height="18" rx="9" />
        <rect className="archive-pill archive-pill--b" x="318" y="397" width="66" height="18" rx="9" />
        <rect className="archive-pill archive-pill--c" x="392" y="397" width="40" height="18" rx="9" />
      </g>
    </svg>
  );
}
