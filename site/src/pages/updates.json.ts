import updates from '../lib/stable-release.json';

export const GET = () => new Response(JSON.stringify(updates), {
  headers: {'Content-Type': 'application/json; charset=utf-8'},
});
