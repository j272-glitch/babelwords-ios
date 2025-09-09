const express = require('express');
const routes = require('./routes');

const app = express();

app.use(express.json());
app.use('/api', routes);

// Static route for privacy policy and data deletion
app.use('/', routes);

const port = process.env.PORT || 5000;
app.listen(port, () => {
  console.log(`Server running on http://localhost:${port}`);
  console.log(`Privacy Policy available at: http://localhost:${port}/policy`);
  console.log(`Data Deletion Instructions: http://localhost:${port}/delete-data`);
});