import React from 'react';
import { Composition } from 'remotion';
import { Promo } from './Promo';

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="Promo"
        component={Promo}
        durationInFrames={510}
        fps={30}
        width={1080}
        height={1920}
      />
    </>
  );
};
