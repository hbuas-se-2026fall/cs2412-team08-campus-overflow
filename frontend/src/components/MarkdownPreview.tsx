import { useEffect, useMemo, useRef } from 'react';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { highlightCode } from '../lib/highlight';
import 'highlight.js/styles/github.css';

marked.setOptions({ gfm: true, breaks: true });

export function MarkdownPreview({ text, emptyHint = '没有内容可预览' }: { text: string; emptyHint?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const html = useMemo(
    () => DOMPurify.sanitize(marked.parse(text, { async: false }) as string),
    [text],
  );

  useEffect(() => {
    highlightCode(ref.current);
  }, [html]);

  if (text.trim() === '') return <p className="muted">{emptyHint}</p>;

  return <div className="markdown-body" ref={ref} dangerouslySetInnerHTML={{ __html: html }} />;
}
