import React from 'react';
import {
  AbsoluteFill,
  Sequence,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';

/**
 * Chanting Buddy v1.4.2 promo: 1080x1920, 17 s @30fps.
 * Cycles the four scenes with their names, shows the motion-level switch,
 * ends on the tagline + Ko-fi line. App palette, Urbanist font (OFL).
 */

const urbanist = {
  fontFamily: 'Urbanist, sans-serif',
};

const FontFace: React.FC = () => (
  <style>{`@font-face {
  font-family: 'Urbanist';
  src: url('/urbanist.ttf') format('truetype');
  font-weight: 100 900;
}`}</style>
);

const BG = '#0E1B1A';
const CARD = '#182826';
const ACCENT = '#6E9A92';
const TEXT = '#F4F8F7';

// One scene card: name + simple animated artwork in the app palette.
const SceneCard: React.FC<{
  name: string;
  children: React.ReactNode;
}> = ({name, children}) => {
  return (
    <div
      style={{
        width: 920,
        height: 1150,
        borderRadius: 64,
        background: CARD,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 60,
      }}
    >
      <div style={{width: 640, height: 640}}>{children}</div>
      <div style={{color: TEXT, fontSize: 72, fontWeight: 800, ...urbanist}}>
        {name}
      </div>
    </div>
  );
};

const MonkScene: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const breath = Math.sin((frame / fps / 5.5) * Math.PI * 2) * 0.5 + 0.5;
  const scale = 1 + (breath - 0.5) * 0.06;
  return (
    <svg viewBox="0 0 200 200" style={{transform: `scale(${scale})`}}>
      <ellipse cx="100" cy="150" rx="72" ry="20" fill="#C97B2A" />
      <path d="M60 150 Q55 96 100 88 Q145 96 140 150 Z" fill="#E8963C" />
      <circle cx="100" cy="62" r="28" fill="#F2C9A4" />
      <path d="M86 60 q6 8 12 0 M102 60 q6 8 12 0" stroke="#8A5A33" strokeWidth="3" fill="none" strokeLinecap="round" />
      <path d="M94 72 q6 6 12 0" stroke="#8A5A33" strokeWidth="3" fill="none" strokeLinecap="round" />
    </svg>
  );
};

const LotusScene: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const open = Math.sin((frame / fps / 5.5) * Math.PI * 2) * 0.5 + 0.5;
  const petal = 40 + open * 14;
  return (
    <svg viewBox="0 0 200 200">
      <rect x="0" y="150" width="200" height="50" fill="#1F3A38" />
      {[0, 1, 2].map((i) => {
        const r = 30 + ((frame / 2 + i * 24) % 72);
        const op = 0.5 - ((frame / 2 + i * 24) % 72) / 144;
        return (
          <ellipse
            key={i}
            cx="100"
            cy="150"
            rx={r}
            ry={r * 0.22}
            fill="none"
            stroke="#6E9A92"
            strokeWidth={2}
            opacity={op}
          />
        );
      })}
      <path d={`M100 150 Q ${100 - petal} 118 78 100 Q 96 118 100 150`} fill="#D983A8" />
      <path d={`M100 150 Q ${100 + petal} 118 122 100 Q 104 118 100 150`} fill="#D983A8" />
      <path d="M100 150 Q 84 108 100 84 Q 116 108 100 150" fill="#E79FBE" />
    </svg>
  );
};

const TempleScene: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const drift = Math.sin((frame / fps / 7) * Math.PI * 2) * 0.5 + 0.5;
  return (
    <svg viewBox="0 0 200 200">
      <circle cx={40 + drift * 120} cy="66" r="46" fill="#FFD9A0" opacity="0.25" />
      <path d="M0 132 Q 44 112 90 128 Q 140 142 200 120 L200 200 L0 200 Z" fill="#2A4A47" />
      <path d="M62 152 L138 152 L132 140 L68 140 Z M70 140 L130 140 L124 128 L76 128 Z M78 128 Q 82 108 100 106 Q 118 108 122 128 Z M97 106 L103 106 L101 78 L99 78 Z" fill="#6E9A92" />
      <path d="M0 156 Q 60 142 120 152 Q 160 158 200 148 L200 200 L0 200 Z" fill="#1C3331" />
    </svg>
  );
};

const AbstractScene: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const b = Math.sin((frame / fps / 5.5) * Math.PI * 2) * 0.5 + 0.5;
  return (
    <svg viewBox="0 0 200 200">
      <circle cx="66" cy="80" r={34 + b * 10} fill="#6E9A92" opacity="0.5" />
      <circle cx="136" cy="62" r={26 + (1 - b) * 8} fill="#8FB5AE" opacity="0.45" />
      <circle cx="106" cy="130" r={40 + b * 8} fill="#547A74" opacity="0.4" />
    </svg>
  );
};

const scenes = [
  {name: 'Monk', el: <MonkScene />},
  {name: 'Lotus', el: <LotusScene />},
  {name: 'Temple dawn', el: <TempleScene />},
  {name: 'Abstract light', el: <AbstractScene />},
];

const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const op = interpolate(frame, [0, 20], [0, 1]);
  const y = interpolate(frame, [0, 30], [40, 0]);
  return (
    <AbsoluteFill
      style={{
        background: BG,
        alignItems: 'center',
        justifyContent: 'center',
        opacity: op,
        transform: `translateY(${y}px)`,
      }}
    >
      <div style={{color: TEXT, fontSize: 84, fontWeight: 900, ...urbanist, textAlign: 'center'}}>
        A gentler, more
        <br />
        beautiful Chanting Buddy
      </div>
      <div style={{color: ACCENT, fontSize: 44, marginTop: 40, ...urbanist}}>
        choose your scene
      </div>
    </AbsoluteFill>
  );
};

const SceneSequence: React.FC<{index: number}> = ({index}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const enter = spring({frame, fps, config: {damping: 22, stiffness: 120}});
  const scene = scenes[index];
  return (
    <AbsoluteFill
      style={{
        background: BG,
        alignItems: 'center',
        justifyContent: 'center',
        opacity: enter,
        transform: `scale(${0.94 + enter * 0.06})`,
      }}
    >
      <SceneCard name={scene.name}>{scene.el}</SceneCard>
    </AbsoluteFill>
  );
};

const MotionLevels: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const highlight = Math.min(2, Math.floor(frame / (fps * 0.9)));
  const levels = ['Calm', 'Minimal', 'Off'];
  return (
    <AbsoluteFill
      style={{background: BG, alignItems: 'center', justifyContent: 'center', gap: 40}}
    >
      <div style={{color: TEXT, fontSize: 64, fontWeight: 800, ...urbanist}}>
        Motion your way
      </div>
      <div style={{display: 'flex', gap: 28}}>
        {levels.map((level, i) => (
          <div
            key={level}
            style={{
              width: 260,
              height: 140,
              borderRadius: 28,
              border: `4px solid ${i === highlight ? ACCENT : '#2A4A47'}`,
              background: i === highlight ? '#1F3A38' : CARD,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: i === highlight ? TEXT : '#9AB8B2',
              fontSize: 44,
              fontWeight: 700,
              ...urbanist,
            }}
          >
            {level}
          </div>
        ))}
      </div>
      <div style={{color: '#9AB8B2', fontSize: 38, ...urbanist}}>
        even respects your phone's “remove animations” setting
      </div>
    </AbsoluteFill>
  );
};

const EndCard: React.FC = () => {
  const frame = useCurrentFrame();
  const op = interpolate(frame, [0, 18], [0, 1]);
  const y = interpolate(frame, [0, 24], [30, 0]);
  return (
    <AbsoluteFill
      style={{
        background: BG,
        alignItems: 'center',
        justifyContent: 'center',
        opacity: op,
        transform: `translateY(${y}px)`,
      }}
    >
      <div style={{color: TEXT, fontSize: 92, fontWeight: 900, ...urbanist, textAlign: 'center'}}>
        Chanting Buddy
      </div>
      <div style={{color: ACCENT, fontSize: 54, marginTop: 28, ...urbanist}}>
        it just chants.
      </div>
      <div style={{color: TEXT, fontSize: 46, marginTop: 90, ...urbanist}}>
        Free on GitHub
      </div>
      <div style={{color: '#9AB8B2', fontSize: 36, marginTop: 480, ...urbanist}}>
        ko-fi.com/xia0da
      </div>
    </AbsoluteFill>
  );
};

export const Promo: React.FC = () => {
  return (
    <AbsoluteFill style={urbanist}>
      <FontFace />
      <Sequence durationInFrames={75}>
        <Intro />
      </Sequence>
      {scenes.map((_, i) => (
        <Sequence key={i} from={75 + i * 75} durationInFrames={75}>
          <SceneSequence index={i} />
        </Sequence>
      ))}
      <Sequence from={375} durationInFrames={75}>
        <MotionLevels />
      </Sequence>
      <Sequence from={450}>
        <EndCard />
      </Sequence>
    </AbsoluteFill>
  );
};
