import hljs from 'highlight.js/lib/common';

export function highlightCode(root: HTMLElement | null) {
  root?.querySelectorAll<HTMLElement>('pre code').forEach((el) => {
    if (el.dataset.highlighted === 'yes') return;
    hljs.highlightElement(el);
  });
}

export { hljs };
