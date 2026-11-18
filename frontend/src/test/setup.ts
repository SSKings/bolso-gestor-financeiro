import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup, configure } from '@testing-library/react';

// Espera dos findBy*/waitFor: 1 s (padrão) estoura com a cobertura ligada e os arquivos rodando em paralelo.
configure({ asyncUtilTimeout: 3000 });

afterEach(() => {
  cleanup();
  localStorage.clear();
});
